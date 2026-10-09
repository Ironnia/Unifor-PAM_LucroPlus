package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.SugestaoPromocaoDto
import br.com.lucroplus.models.PreviaPromocaoDto
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

object MotorPromocaoService {
    suspend fun listarSugestoes(hoje: LocalDate = LocalDate.now()): List<SugestaoPromocaoDto> = dbQuery {
        val lotesSalvos = (AcoesAlertaTable innerJoin LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where {
                (AcoesAlertaTable.acao eq AcaoAlerta.SALVAR_LOTE.name) and
                    (LotesTable.quantidadeG greater 0) and
                    (LotesTable.dataValidade greaterEq RegraValidadeLote.validadeMinima(hoje).toKotlinLocalDate()) and
                    (LotesTable.dataValidade lessEq RegraValidadeLote.validadeMaxima(hoje).toKotlinLocalDate())
            }
            .orderBy(LotesTable.dataValidade, SortOrder.ASC)
            .map { row ->
                LotePromocao(
                    row[LotesTable.id], row[LotesTable.numeroLote] ?: "LOT-${row[LotesTable.id]}",
                    row[LotesTable.ingredienteId], row[IngredientesTable.nome],
                    LocalDate.parse(row[LotesTable.dataValidade].toString()), row[LotesTable.custoUnitario]
                )
            }
        val encaminhados = PromocoesTable.selectAll().mapNotNull { it[PromocoesTable.loteId] }.toSet()
        calcularSugestoes(lotesSalvos.filter { it.id !in encaminhados }, hoje)
    }

    suspend fun listarPrevias(hoje: LocalDate = LocalDate.now()): List<PreviaPromocaoDto> = dbQuery {
        val lotesAtivos = (AlertasTable innerJoin LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where {
                (AlertasTable.tipo eq "VENCIMENTO") and
                    (AlertasTable.visualizado eq false) and
                    (LotesTable.quantidadeG greater 0) and
                    (LotesTable.dataValidade greaterEq RegraValidadeLote.validadeMinima(hoje).toKotlinLocalDate()) and
                    (LotesTable.dataValidade lessEq RegraValidadeLote.validadeMaxima(hoje).toKotlinLocalDate())
            }
            .orderBy(LotesTable.id, SortOrder.ASC)
            .map { row ->
                LotePromocao(
                    row[LotesTable.id], row[LotesTable.numeroLote] ?: "LOT-${row[LotesTable.id]}",
                    row[LotesTable.ingredienteId], row[IngredientesTable.nome],
                    LocalDate.parse(row[LotesTable.dataValidade].toString()), row[LotesTable.custoUnitario]
                )
            }
        calcularSugestoes(lotesAtivos, hoje)
            .groupBy { it.loteId }
            .mapNotNull { (loteId, candidatos) ->
                candidatos.filter { it.vendas30Dias > 0 }
                    .sortedWith(compareByDescending<SugestaoPromocaoDto> { it.vendas30Dias }.thenBy { it.pratoId })
                    .firstOrNull()?.let { escolhido ->
                        PreviaPromocaoDto(loteId, escolhido.pratoId, escolhido.pratoNome,
                            escolhido.melhorDiaSemana, escolhido.dataSugerida)
                    }
            }
            .sortedBy { it.loteId }
    }

    private fun calcularSugestoes(lotes: List<LotePromocao>, hoje: LocalDate): List<SugestaoPromocaoDto> {
        if (lotes.isEmpty()) return emptyList()

        val fichas = (FichasTecnicasTable innerJoin ProdutosTable)
            .selectAll().where { ProdutosTable.ativo eq true }
            .map { row ->
                ItemFicha(
                    row[ProdutosTable.id], row[ProdutosTable.nome], row[ProdutosTable.preco],
                    row[FichasTecnicasTable.ingredienteId], row[FichasTecnicasTable.quantidadeUsada],
                    row[FichasTecnicasTable.unidade]
                )
            }
        val fichasPorProduto = fichas.groupBy { it.produtoId }
        val custoPorIngrediente = LotesTable.selectAll()
            .where {
                (LotesTable.quantidadeG greater 0) and
                    (LotesTable.dataValidade greaterEq hoje.toKotlinLocalDate())
            }
            .orderBy(LotesTable.dataEntrada to SortOrder.ASC, LotesTable.id to SortOrder.ASC)
            .map { it[LotesTable.ingredienteId] to it[LotesTable.custoUnitario] }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.first() } // primeiro lote fifo
        val pesosPorUnidade = IngredientesTable.selectAll()
            .associate { it[IngredientesTable.id] to it[IngredientesTable.pesoPorUnidadeG] }

        val vendas = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where {
                (VendasTable.dataVenda greaterEq hoje.minusDays(29).toKotlinLocalDate()) and
                    (VendasTable.dataVenda lessEq hoje.toKotlinLocalDate())
            }
            .map { row ->
                VendaPromocao(
                    row[ItensVendaTable.produtoId],
                    LocalDate.parse(row[VendasTable.dataVenda].toString()),
                    row[ItensVendaTable.quantidade]
                )
            }.groupBy { it.produtoId }

        return lotes.flatMap { lote ->
            fichasPorProduto.values
                .filter { itens -> itens.any { it.ingredienteId == lote.ingredienteId } }
                .map { itens ->
                    val prato = itens.first()
                    val custos = itens.map { item ->
                        val custoGrama = if (item.ingredienteId == lote.ingredienteId) {
                            lote.custoGrama
                        } else custoPorIngrediente[item.ingredienteId]
                        CalculoSugestaoPromocao.custoItem(
                            item.quantidade, item.unidade,
                            pesosPorUnidade[item.ingredienteId], custoGrama
                        )
                    }
                    val custo = custos.takeIf { lista -> lista.all { it != null } }
                        ?.filterNotNull()?.sumOf { it }
                    val margem = custo?.let { CalculoSugestaoPromocao.margemPct(prato.preco, it) }
                    val lucroBruto = custo?.let { prato.preco.subtract(it).setScale(2, RoundingMode.HALF_UP) }
                    val historico = vendas[prato.produtoId].orEmpty()
                    val dia = CalculoSugestaoPromocao.melhorDia(
                        historico.map { it.data to it.quantidade }, hoje, lote.prazoLimite
                    )
                    val dias = RegraValidadeLote.diasAtePrazo(lote.validade, hoje)
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
                        precoVenda = prato.preco.toDouble(),
                        lucroBrutoEstimado = lucroBruto?.toDouble(),
                        margemPct = margem?.toDouble(),
                        limiteDescontoMargemBrutaPct = custo?.let {
                            CalculoSugestaoPromocao.limiteDescontoPositivo(prato.preco, it)
                        },
                        observacao = when {
                            margem == null -> "Margem indisponível: falta custo de insumo ou unidade válida na ficha técnica."
                            dia == null -> "Sem vendas dos últimos 30 dias em um dia disponível antes do prazo."
                            else -> null
                        }
                    )
                }
        }.sortedWith(compareBy<SugestaoPromocaoDto> { it.prazoLimiteVenda }.thenBy { it.loteId }.thenBy { it.pratoId })
    }


}

private data class LotePromocao(
    val id: Long, val numero: String, val ingredienteId: Long, val ingredienteNome: String,
    val validade: LocalDate, val custoGrama: BigDecimal
) {
    val prazoLimite: LocalDate get() = RegraValidadeLote.prazoLimiteVenda(validade)
}

private data class ItemFicha(
    val produtoId: Long, val produtoNome: String, val preco: BigDecimal,
    val ingredienteId: Long, val quantidade: BigDecimal, val unidade: String
)

private data class VendaPromocao(val produtoId: Long, val data: LocalDate, val quantidade: Int)

// funcoes de calculo de desconto e margem
object CalculoSugestaoPromocao {
    fun limiteDescontoPositivo(preco: BigDecimal, custo: BigDecimal): Int? {
        if (preco <= BigDecimal.ZERO || custo < BigDecimal.ZERO) return null
        val custoCentavos = custo.setScale(2, RoundingMode.HALF_UP)
        return (0..99).lastOrNull { desconto ->
            preco.multiply(BigDecimal(100 - desconto))
                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP) > custoCentavos
        }
    }

    fun custoItem(quantidade: BigDecimal, unidade: String, pesoPorUnidadeG: Int?, custoGrama: BigDecimal?): BigDecimal? {
        if (quantidade <= BigDecimal.ZERO || custoGrama == null || custoGrama < BigDecimal.ZERO) return null
        val gramas = when (unidade.lowercase()) {
            "g" -> quantidade
            "kg", "l" -> quantidade.multiply(BigDecimal(1000))
            "un" -> pesoPorUnidadeG?.takeIf { it > 0 }?.let { quantidade.multiply(BigDecimal(it)) }
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
