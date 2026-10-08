package br.com.lucroplus.services

import br.com.lucroplus.database.AcoesAlertaTable
import br.com.lucroplus.database.AlertasTable
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.models.AlertaDto
import br.com.lucroplus.models.IngredienteResumoDto
import br.com.lucroplus.models.LoteResumoDto
import br.com.lucroplus.models.LotePendentePromocaoDto
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate

enum class AcaoAlerta { SALVAR_LOTE, CIENTE }
enum class ResultadoAcaoAlerta { CONCLUIDA, JA_APLICADA, CONFLITO, NAO_ENCONTRADO }

interface AlertaOperacoes {
    suspend fun obterAlertasVencimento(): List<AlertaDto>
    suspend fun executarAcao(alertaId: Long, acao: AcaoAlerta): ResultadoAcaoAlerta
    suspend fun listarLotesPendentes(): List<LotePendentePromocaoDto>
}

object AlertaService : AlertaOperacoes {

    override suspend fun obterAlertasVencimento(): List<AlertaDto> = dbQuery {
        val hojeJava = LocalDate.now()
        val dataLimiteJava = RegraValidadeLote.validadeMaxima(hojeJava)

        val hojeKmp = hojeJava.toKotlinLocalDate()
        val validadeMinimaKmp = RegraValidadeLote.validadeMinima(hojeJava).toKotlinLocalDate()
        val dataLimiteKmp = dataLimiteJava.toKotlinLocalDate()

        // busca lotes proximos do vencimento
        val lotesEmRisco = (LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where {
                (LotesTable.dataValidade greaterEq validadeMinimaKmp) and
                    (LotesTable.dataValidade lessEq dataLimiteKmp) and
                    (LotesTable.quantidadeG greater 0)
            }
            .orderBy(LotesTable.id, SortOrder.ASC)
            .forUpdate()
            .toList()

        // cria alerta se nao existir
        for (row in lotesEmRisco) {
            val loteId = row[LotesTable.id]
            val validadeKmp = row[LotesTable.dataValidade]
            val validadeJava = LocalDate.parse(validadeKmp.toString())
            val diasParaPrazo = RegraValidadeLote.diasAtePrazo(validadeJava, hojeJava)

            val alertaExiste = AlertasTable
                .selectAll()
                .where { (AlertasTable.loteId eq loteId) and (AlertasTable.tipo eq "VENCIMENTO") }
                .count() > 0

            if (!alertaExiste) {
                val ingredienteNome = row[IngredientesTable.nome]
                val quantidade = row[LotesTable.quantidadeG]
                val numeroLote = row[LotesTable.numeroLote] ?: "LOT-$loteId"

                val mensagem = mensagemValidade(numeroLote, ingredienteNome, quantidade, diasParaPrazo)

                AlertasTable.insert {
                    it[AlertasTable.loteId] = loteId
                    it[AlertasTable.tipo] = "VENCIMENTO"
                    it[AlertasTable.mensagem] = mensagem
                    it[AlertasTable.dataAlerta] = hojeKmp
                    it[AlertasTable.visualizado] = false
                }
            }
        }

        // lista alertas pendentes
        (AlertasTable innerJoin LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where {
                (AlertasTable.visualizado eq false) and
                    (AlertasTable.tipo eq "VENCIMENTO") and
                    (LotesTable.dataValidade greaterEq validadeMinimaKmp) and
                    (LotesTable.dataValidade lessEq dataLimiteKmp) and
                    (LotesTable.quantidadeG greater 0)
            }
            .orderBy(LotesTable.dataValidade, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map {
                val validadeJava = LocalDate.parse(it[LotesTable.dataValidade].toString())
                val diasParaPrazo = RegraValidadeLote.diasAtePrazo(validadeJava, hojeJava)
                val loteResumo = LoteResumoDto(
                    id = it[LotesTable.id],
                    quantidade = it[LotesTable.quantidadeG].toDouble(),
                    custoUnitario = it[LotesTable.custoUnitario].toDouble(),
                    ingrediente = IngredienteResumoDto(
                        nome = it[IngredientesTable.nome],
                        unidade = it[IngredientesTable.unidade]
                    )
                )

                AlertaDto(
                    id = it[AlertasTable.id],
                    loteId = it[AlertasTable.loteId],
                    tipo = it[AlertasTable.tipo],
                    mensagem = mensagemValidade(
                        it[LotesTable.numeroLote] ?: "LOT-${it[LotesTable.id]}",
                        it[IngredientesTable.nome],
                        it[LotesTable.quantidadeG],
                        diasParaPrazo
                    ),
                    dataAlerta = it[AlertasTable.dataAlerta].toString(),
                    visualizado = it[AlertasTable.visualizado],
                    dataValidade = validadeJava.toString(),
                    prazoLimiteVenda = RegraValidadeLote.prazoLimiteVenda(validadeJava).toString(),
                    diasParaPrazoLimite = diasParaPrazo,
                    criticidade = RegraValidadeLote.criticidade(diasParaPrazo),
                    lote = loteResumo
                )
            }
    }

    private fun mensagemValidade(
        numeroLote: String,
        ingredienteNome: String,
        quantidadeG: Int,
        diasParaPrazo: Int
    ): String {
        val prazo = when (diasParaPrazo) {
            0 -> "atinge o prazo limite de venda hoje"
            1 -> "atinge o prazo limite de venda amanhã"
            else -> "atinge o prazo limite de venda em $diasParaPrazo dias"
        }
        val nivel = RegraValidadeLote.criticidade(diasParaPrazo)
        return "O lote $numeroLote de $ingredienteNome ($quantidadeG g) $prazo. $nivel."
    }

    // trava o lote para evitar acoes concorrentes duplicadas
    override suspend fun executarAcao(alertaId: Long, acao: AcaoAlerta): ResultadoAcaoAlerta = dbQuery {
        val alertaInicial = AlertasTable.selectAll()
            .where { (AlertasTable.id eq alertaId) and (AlertasTable.tipo eq "VENCIMENTO") }
            .singleOrNull() ?: return@dbQuery ResultadoAcaoAlerta.NAO_ENCONTRADO
        val loteId = alertaInicial[AlertasTable.loteId]

        val lote = LotesTable.selectAll().where { LotesTable.id eq loteId }
            .forUpdate().singleOrNull() ?: return@dbQuery ResultadoAcaoAlerta.NAO_ENCONTRADO
        val alerta = AlertasTable.selectAll().where { AlertasTable.id eq alertaId }
            .forUpdate().singleOrNull() ?: return@dbQuery ResultadoAcaoAlerta.NAO_ENCONTRADO
        val decisao = AcoesAlertaTable.selectAll()
            .where { AcoesAlertaTable.loteId eq loteId }.forUpdate().singleOrNull()
        if (decisao != null) {
            return@dbQuery if (decisao[AcoesAlertaTable.acao] == acao.name) {
                ResultadoAcaoAlerta.JA_APLICADA
            } else {
                ResultadoAcaoAlerta.CONFLITO
            }
        }
        if (alerta[AlertasTable.visualizado] ||
            lote[LotesTable.quantidadeG] <= 0 ||
            RegraValidadeLote.prazoLimiteVenda(LocalDate.parse(lote[LotesTable.dataValidade].toString())).isBefore(LocalDate.now())
        ) {
            return@dbQuery ResultadoAcaoAlerta.CONFLITO
        }

        AcoesAlertaTable.insert {
            it[AcoesAlertaTable.loteId] = loteId
            it[AcoesAlertaTable.alertaId] = alertaId
            it[AcoesAlertaTable.acao] = acao.name
            it[AcoesAlertaTable.dataAcao] = LocalDate.now().toKotlinLocalDate()
        }
        // marca como visualizado
        AlertasTable.update({ (AlertasTable.loteId eq loteId) and (AlertasTable.tipo eq "VENCIMENTO") }) {
            it[visualizado] = true
        }
        ResultadoAcaoAlerta.CONCLUIDA
    }

    override suspend fun listarLotesPendentes(): List<LotePendentePromocaoDto> = dbQuery {
        (AcoesAlertaTable innerJoin LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where { AcoesAlertaTable.acao eq AcaoAlerta.SALVAR_LOTE.name }
            .orderBy(LotesTable.dataValidade, SortOrder.ASC)
            .map { row ->
                val validade = LocalDate.parse(row[LotesTable.dataValidade].toString())
                LotePendentePromocaoDto(
                    alertaId = row[AcoesAlertaTable.alertaId],
                    loteId = row[LotesTable.id],
                    numeroLote = row[LotesTable.numeroLote] ?: "LOT-${row[LotesTable.id]}",
                    ingredienteNome = row[IngredientesTable.nome],
                    prazoLimiteVenda = RegraValidadeLote.prazoLimiteVenda(validade).toString(),
                    criticidade = RegraValidadeLote.criticidade(
                        RegraValidadeLote.diasAtePrazo(validade, LocalDate.now())
                    ),
                    dataAcao = row[AcoesAlertaTable.dataAcao].toString()
                )
            }
    }
}
