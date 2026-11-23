package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.SugestaoPromocaoDto
import br.com.lucroplus.models.AvisoSugestaoDto
import br.com.lucroplus.models.StatusPromocao
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

class MotorPromocaoService(private val restauranteId: Long) {
    suspend fun listarSugestoes(hoje: LocalDate = LocalDate.now()): List<SugestaoPromocaoDto> = dbQuery {
        val campanhas = PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.loteId.isNotNull()) }.toList()
        val lotesOcupados = campanhas.filter { it[PromocoesTable.status] in listOf(
            StatusPromocao.EM_ANDAMENTO.name, StatusPromocao.CONCLUIDA_SUCESSO.name,
            StatusPromocao.EXPIRADA_COM_PERDA.name
        ) }
            .mapNotNull { it[PromocoesTable.loteId] }.toSet()
        val paresRecusados = campanhas.filter { it[PromocoesTable.status] == "RECUSADA" }
            .map { it[PromocoesTable.loteId] to it[PromocoesTable.produtoId] }.toSet()
        val lotesSalvos = (AcoesAlertaTable innerJoin LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where { (IngredientesTable.restauranteId eq restauranteId) and (LotesTable.restauranteId eq restauranteId) and ((AcoesAlertaTable.acao eq AcaoAlerta.SALVAR_LOTE.name) and
                    (LotesTable.quantidadeG greater 0) and
                    (LotesTable.dataValidade greaterEq RegraValidadeLote.validadeMinima(hoje).toKotlinLocalDate()) and
                    (LotesTable.dataValidade lessEq RegraValidadeLote.validadeMaxima(hoje).toKotlinLocalDate())) }
            .orderBy(LotesTable.dataValidade, SortOrder.ASC)
            .map { row ->
                LotePromocao(
                    row[LotesTable.id], row[LotesTable.numeroLote] ?: "LOT-${row[LotesTable.id]}",
                    row[LotesTable.ingredienteId], row[IngredientesTable.nome],
                    RegraValidadeLote.prazoLimiteVenda(LocalDate.parse(row[LotesTable.dataValidade].toString())), row[LotesTable.custoUnitario]
                )
            }
        if (lotesSalvos.isEmpty()) return@dbQuery emptyList()

        val fichas = (FichasTecnicasTable innerJoin ProdutosTable innerJoin IngredientesTable)
            .selectAll().where { (ProdutosTable.restauranteId eq restauranteId) and (IngredientesTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .map { row ->
                ItemFicha(
                    row[ProdutosTable.id], row[ProdutosTable.nome], row[ProdutosTable.preco],
                    row[FichasTecnicasTable.ingredienteId], row[FichasTecnicasTable.quantidadeUsada],
                    row[FichasTecnicasTable.unidade]
                )
            }
        val fichasPorProduto = fichas.groupBy { it.produtoId }
        val custoPorIngrediente = LotesTable.selectAll()
            .where { (LotesTable.restauranteId eq restauranteId) and ((LotesTable.quantidadeG greater 0) and
                    (LotesTable.dataValidade greaterEq RegraValidadeLote.validadeMinima(hoje).toKotlinLocalDate())) }
            .orderBy(LotesTable.dataValidade, SortOrder.ASC)
            .map { it[LotesTable.ingredienteId] to it[LotesTable.custoUnitario] }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.first() }
        val ingredientes = IngredientesTable.selectAll().where { (IngredientesTable.restauranteId eq restauranteId) }.associateBy { it[IngredientesTable.id] }

        val vendas = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and ((VendasTable.dataVenda greaterEq hoje.minusDays(29).toKotlinLocalDate()) and
                    (VendasTable.dataVenda lessEq hoje.toKotlinLocalDate())) }
            .map { row ->
                VendaPromocao(
                    row[ItensVendaTable.produtoId],
                    LocalDate.parse(row[VendasTable.dataVenda].toString()),
                    row[ItensVendaTable.quantidade]
                )
            }.groupBy { it.produtoId }

        lotesSalvos.filter { it.id !in lotesOcupados }.flatMap { lote ->
            fichasPorProduto.values
                .filter { itens -> itens.any { it.ingredienteId == lote.ingredienteId } }
                .filter { itens -> (lote.id to itens.first().produtoId) !in paresRecusados }
                .map { itens ->
                    val prato = itens.first()
                    val avisos = mutableListOf<AvisoSugestaoDto>()
                    val custos = itens.map { item ->
                        val custoGrama = if (item.ingredienteId == lote.ingredienteId) {
                            lote.custoGrama
                        } else custoPorIngrediente[item.ingredienteId]
                        val ingrediente = ingredientes[item.ingredienteId]
                        val peso = ingrediente?.get(IngredientesTable.pesoPorUnidadeG)
                        avisos += DiagnosticoSugestaoPromocao.custoItem(
                            item.ingredienteId, ingrediente?.get(IngredientesTable.nome) ?: "Ingrediente #${item.ingredienteId}",
                            item.quantidade, item.unidade, peso, custoGrama
                        )
                        CalculoSugestaoPromocao.custoItem(
                            item.quantidade, item.unidade,
                            peso, custoGrama
                        )
                    }
                    val margem = if (custos.all { it != null }) {
                        CalculoSugestaoPromocao.margemPct(prato.preco, custos.filterNotNull().sumOf { it })
                    } else null
                    val historico = vendas[prato.produtoId].orEmpty()
                    val dia = CalculoSugestaoPromocao.melhorDia(
                        historico.map { it.data to it.quantidade }, hoje, lote.prazoLimite
                    )
                    if (prato.preco <= BigDecimal.ZERO) avisos += AvisoSugestaoDto(
                        "MARGEM", "PRECO_INVALIDO", "Margem indisponível: o preço de venda do prato deve ser maior que zero."
                    )
                    if (dia == null) avisos += if (historico.none { it.quantidade > 0 }) {
                        AvisoSugestaoDto("MELHOR_DIA", "SEM_VENDAS_RECENTES",
                            "Melhor dia indisponível: não há vendas válidas deste prato nos últimos 30 dias.")
                    } else {
                        AvisoSugestaoDto("MELHOR_DIA", "SEM_DIA_COMPATIVEL",
                            "Há histórico de vendas, mas nenhum dia correspondente está disponível até o prazo deste lote.")
                    }
                    val dias = java.time.temporal.ChronoUnit.DAYS.between(hoje, lote.prazoLimite).toInt()
                    SugestaoPromocaoDto(
                        loteId = lote.id,
                        numeroLote = lote.numero,
                        ingredienteNome = lote.ingredienteNome,
                        pratoId = prato.produtoId,
                        pratoNome = prato.produtoNome,
                        prazoLimiteVenda = lote.prazoLimite.toString(),
                        diasParaPrazoLimite = dias,
                        criticidade = RegraValidadeLote.criticidade(dias),
                        melhorDiaSemana = dia?.dayOfWeek?.nomeEmPortugues(),
                        dataSugerida = dia?.toString(),
                        vendas30Dias = historico.sumOf { it.quantidade },
                        margemPct = margem?.toDouble(),
                        precoVenda = prato.preco.toDouble(),
                        lucroBrutoEstimado = custos.takeIf { valores -> valores.all { it != null } }
                            ?.filterNotNull()?.sumOf { it }?.let { prato.preco.subtract(it).toDouble() },
                        limiteDescontoMargemBrutaPct = margem?.takeIf { it > BigDecimal.ZERO }?.toInt(),

                        observacao = avisos.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.mensagem },
                        avisos = avisos
                    )
                }
        }.sortedWith(compareBy<SugestaoPromocaoDto> { it.prazoLimiteVenda }.thenBy { it.loteId }.thenBy { it.pratoId })
    }

}

private data class LotePromocao(
    val id: Long, val numero: String, val ingredienteId: Long, val ingredienteNome: String,
    val prazoLimite: LocalDate, val custoGrama: BigDecimal
)

private data class ItemFicha(
    val produtoId: Long, val produtoNome: String, val preco: BigDecimal,
    val ingredienteId: Long, val quantidade: BigDecimal, val unidade: String
)

private data class VendaPromocao(val produtoId: Long, val data: LocalDate, val quantidade: Int)

internal object DiagnosticoSugestaoPromocao {
    fun custoItem(id: Long, nome: String, quantidade: BigDecimal, unidade: String,
                  pesoPorUnidadeG: Int?, custoGrama: BigDecimal?): List<AvisoSugestaoDto> = buildList {
        fun aviso(codigo: String, motivo: String) = AvisoSugestaoDto(
            "MARGEM", codigo, "Margem indisponível: $motivo", id, nome
        )
        if (quantidade <= BigDecimal.ZERO) add(aviso("QUANTIDADE_INVALIDA", "a quantidade de $nome na ficha técnica deve ser maior que zero."))
        if (custoGrama == null) add(aviso("SEM_CUSTO_ELEGIVEL", "$nome não possui lote com saldo e validade vigente para referência de custo."))
        else if (custoGrama < BigDecimal.ZERO) add(aviso("CUSTO_INVALIDO", "o custo de $nome não pode ser negativo."))
        when (unidade.lowercase()) {
            "g", "kg" -> Unit
            "un", "l" -> if (pesoPorUnidadeG == null || pesoPorUnidadeG <= 0) {
                add(aviso("PESO_UNIDADE_INVALIDO", "informe um peso por unidade válido para $nome."))
            }
            else -> add(aviso("UNIDADE_INVALIDA", "confira a unidade de medida de $nome na ficha técnica ($unidade)."))
        }
    }
}

object CalculoSugestaoPromocao {
    fun custoItem(quantidade: BigDecimal, unidade: String, pesoPorUnidadeG: Int?, custoGrama: BigDecimal?): BigDecimal? {
        if (quantidade <= BigDecimal.ZERO || custoGrama == null || custoGrama < BigDecimal.ZERO) return null
        val gramas = when (unidade.lowercase()) {
            "g" -> quantidade
            "kg" -> quantidade.multiply(BigDecimal(1000))
            "un", "l" -> pesoPorUnidadeG?.takeIf { it > 0 }?.let { quantidade.multiply(BigDecimal(it)) }
            else -> null
        } ?: return null
        return gramas.multiply(custoGrama)
    }

    fun margemPct(preco: BigDecimal, custo: BigDecimal): BigDecimal? =
        if (preco <= BigDecimal.ZERO || custo < BigDecimal.ZERO) null
        else preco.subtract(custo).multiply(BigDecimal(100))
            .divide(preco, 2, RoundingMode.HALF_UP)

    fun melhorDia(vendas: List<Pair<LocalDate, Int>>, hoje: LocalDate, prazo: LocalDate): LocalDate? {
        if (prazo.isBefore(hoje)) return null
        val inicio = hoje.minusDays(29)
        val volumePorDia = vendas.asSequence()
            .filter { (data, qtd) -> !data.isBefore(inicio) && !data.isAfter(hoje) && qtd > 0 }
            .groupBy({ it.first.dayOfWeek }, { it.second })
            .mapValues { (_, quantidades) -> quantidades.sum() }
        if (volumePorDia.isEmpty()) return null
        return generateSequence(hoje) { it.plusDays(1) }
            .takeWhile { !it.isAfter(prazo) }
            .filter { it.dayOfWeek in volumePorDia }
            .sortedWith(compareByDescending<LocalDate> { volumePorDia[it.dayOfWeek] }.thenBy { it })
            .firstOrNull()
    }
}

private fun DayOfWeek.nomeEmPortugues(): String = when (this) {
    DayOfWeek.MONDAY -> "Segunda-feira"
    DayOfWeek.TUESDAY -> "Terça-feira"
    DayOfWeek.WEDNESDAY -> "Quarta-feira"
    DayOfWeek.THURSDAY -> "Quinta-feira"
    DayOfWeek.FRIDAY -> "Sexta-feira"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}
