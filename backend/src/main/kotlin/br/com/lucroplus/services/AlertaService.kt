package br.com.lucroplus.services

import br.com.lucroplus.database.AlertasTable
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.models.AlertaDto
import br.com.lucroplus.models.IngredienteResumoDto
import br.com.lucroplus.models.LoteResumoDto
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate

object AlertaService {


    suspend fun obterAlertasVencimento(): List<AlertaDto> = dbQuery {
        val hojeJava = LocalDate.now()
        val dataLimiteJava = RegraValidadeLote.validadeMaxima(hojeJava)

        val hojeKmp = hojeJava.toKotlinLocalDate()
        val validadeMinimaKmp = RegraValidadeLote.validadeMinima(hojeJava).toKotlinLocalDate()
        val dataLimiteKmp = dataLimiteJava.toKotlinLocalDate()
        val lotesEmRisco = (LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where {
                (LotesTable.dataValidade greaterEq validadeMinimaKmp) and
                    (LotesTable.dataValidade lessEq dataLimiteKmp) and
                    (LotesTable.quantidadeG greater 0)
            }
            .toList()
        for (row in lotesEmRisco) {
            val loteId = row[LotesTable.id]
            val validadeKmp = row[LotesTable.dataValidade]
            val validadeJava = LocalDate.parse(validadeKmp.toString())
            val diasParaVencer = RegraValidadeLote.diasAtePrazo(validadeJava, hojeJava)

            val alertaExiste = AlertasTable
                .selectAll()
                .where { (AlertasTable.loteId eq loteId) and (AlertasTable.tipo eq "VENCIMENTO") }
                .count() > 0

            if (!alertaExiste) {
                val ingredienteNome = row[IngredientesTable.nome]
                val quantidade = row[LotesTable.quantidadeG]
                val numeroLote = row[LotesTable.numeroLote] ?: "LOT-$loteId"

                val mensagem = mensagemValidade(numeroLote, ingredienteNome, quantidade, diasParaVencer)

                AlertasTable.insert {
                    it[AlertasTable.loteId] = loteId
                    it[AlertasTable.tipo] = "VENCIMENTO"
                    it[AlertasTable.mensagem] = mensagem
                    it[AlertasTable.dataAlerta] = hojeKmp
                    it[AlertasTable.visualizado] = false
                }
            }
        }
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


    suspend fun marcarComoVisualizado(alertaId: Long): Boolean = dbQuery {
        val rowsUpdated = AlertasTable.update({ AlertasTable.id eq alertaId }) {
            it[visualizado] = true
        }
        rowsUpdated > 0
    }
}
