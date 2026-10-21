package br.com.lucroplus.services

import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.FichasTecnicasTable
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.ItensVendaTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.database.VendasTable
import br.com.lucroplus.models.RankingItemDto
import br.com.lucroplus.models.MesDesempenhoDto
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class RankingService(private val restauranteId: Long) {
    suspend fun listar(
        hoje: LocalDate = LocalDate.now(ZoneId.of("America/Fortaleza"))
    ): List<RankingItemDto> {
        val ranking = dbQuery {
            val produtos = ProdutosTable.selectAll()
                .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
                .orderBy(ProdutosTable.id, SortOrder.ASC)
                .toList()
            if (produtos.isEmpty()) return@dbQuery emptyList()

        val ingredientes = IngredientesTable.selectAll()
            .where { IngredientesTable.restauranteId eq restauranteId }
            .associateBy { it[IngredientesTable.id] }

        val custoPorIngrediente = LotesTable.selectAll()
            .where { (LotesTable.restauranteId eq restauranteId) and (LotesTable.quantidadeG greater 0) }
            .groupBy { it[LotesTable.ingredienteId] }
            .mapValues { (_, lotes) ->
                CalculoRanking.custoPonderado(lotes.map {
                    it[LotesTable.quantidadeG] to it[LotesTable.custoUnitario]
                })
            }

        val fichas = FichasTecnicasTable.selectAll()
            .where { FichasTecnicasTable.produtoId inList produtos.map { it[ProdutosTable.id] } }
            .groupBy { it[FichasTecnicasTable.produtoId] }

        val entradas = produtos.mapNotNull { produto ->
            val itens = fichas[produto[ProdutosTable.id]].orEmpty()
            if (itens.isEmpty()) return@mapNotNull null

            val custos = itens.map { item ->
                val ingredienteId = item[FichasTecnicasTable.ingredienteId]
                val ingrediente = ingredientes[ingredienteId] ?: return@mapNotNull null
                val custoGrama = custoPorIngrediente[ingredienteId] ?: return@mapNotNull null
                val quantidadeG = CalculoRanking.converterParaGramas(
                    item[FichasTecnicasTable.quantidadeUsada],
                    item[FichasTecnicasTable.unidade],
                    ingrediente[IngredientesTable.pesoPorUnidadeG]
                ) ?: return@mapNotNull null
                quantidadeG.multiply(custoGrama)
            }

            ProdutoRanking(
                produtoId = produto[ProdutosTable.id],
                nome = produto[ProdutosTable.nome],
                preco = produto[ProdutosTable.preco],
                cmv = custos.sumOf { it }
            )
        }

        val inicioAtual = hoje.withDayOfMonth(1)
        val mesAnterior = YearMonth.from(hoje).minusMonths(1)
        val comparavel = hoje.dayOfMonth <= mesAnterior.lengthOfMonth()
        val inicioAnterior = mesAnterior.atDay(1)
        val fimAnterior = if (comparavel) mesAnterior.atDay(hoje.dayOfMonth) else mesAnterior.atEndOfMonth()

        val vendas = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where {
                (VendasTable.restauranteId eq restauranteId) and
                    (VendasTable.dataVenda greaterEq inicioAnterior.toKotlinLocalDate()) and
                    (VendasTable.dataVenda lessEq hoje.toKotlinLocalDate())
            }
            .toList()

        fun unidades(inicio: LocalDate, fim: LocalDate): Map<Long, Int> = vendas
            .filter {
                val data = LocalDate.parse(it[VendasTable.dataVenda].toString())
                !data.isBefore(inicio) && !data.isAfter(fim)
            }
            .groupBy { it[ItensVendaTable.produtoId] }
            .mapValues { (_, itens) -> itens.sumOf { it[ItensVendaTable.quantidade] } }

            val calculado = CalculoRanking.calcular(
                produtos = entradas,
                vendasAtuais = unidades(inicioAtual, hoje),
                vendasAnteriores = if (comparavel) unidades(inicioAnterior, fimAnterior) else emptyMap(),
                periodoAnteriorComparavel = comparavel
            )
            val meses = (5 downTo 0).map { YearMonth.from(hoje).minusMonths(it.toLong()) }
            val vendasHistoricas = (ItensVendaTable innerJoin VendasTable).selectAll().where {
                (VendasTable.restauranteId eq restauranteId) and
                    (VendasTable.dataVenda greaterEq meses.first().atDay(1).toKotlinLocalDate()) and
                    (VendasTable.dataVenda lessEq hoje.toKotlinLocalDate())
            }.toList()
            val lotesHistoricos = LotesTable.selectAll()
                .where { LotesTable.restauranteId eq restauranteId }
                .groupBy { it[LotesTable.ingredienteId] }

            calculado.map { item ->
                val serie = meses.mapNotNull { mes ->
                    val itensMes = vendasHistoricas.filter {
                        it[ItensVendaTable.produtoId] == item.produtoId &&
                            YearMonth.from(LocalDate.parse(it[VendasTable.dataVenda].toString())) == mes
                    }
                    val unidadesMes = itensMes.sumOf { it[ItensVendaTable.quantidade] }
                    if (unidadesMes == 0) return@mapNotNull MesDesempenhoDto(mes.toString(), 0, 0.0)
                    val custos = fichas[item.produtoId].orEmpty().map { ficha ->
                        val ingredienteId = ficha[FichasTecnicasTable.ingredienteId]
                        val ingrediente = ingredientes[ingredienteId] ?: return@mapNotNull null
                        val quantidadeG = CalculoRanking.converterParaGramas(
                            ficha[FichasTecnicasTable.quantidadeUsada],
                            ficha[FichasTecnicasTable.unidade],
                            ingrediente[IngredientesTable.pesoPorUnidadeG]
                        ) ?: return@mapNotNull null
                        val custo = lotesHistoricos[ingredienteId].orEmpty()
                            .filter { LocalDate.parse(it[LotesTable.dataEntrada].toString()) <= mes.atEndOfMonth() }
                            .maxWithOrNull(compareBy({ it[LotesTable.dataEntrada].toString() }, { it[LotesTable.id] }))
                            ?.get(LotesTable.custoUnitario) ?: return@mapNotNull null
                        quantidadeG.multiply(custo)
                    }
                    val cmv = custos.sumOf { it }
                    val lucro = itensMes.sumOf { venda ->
                        venda[ItensVendaTable.precoUnitario].subtract(cmv)
                            .multiply(BigDecimal(venda[ItensVendaTable.quantidade]))
                    }
                    MesDesempenhoDto(mes.toString(), unidadesMes, lucro.setScale(2, RoundingMode.HALF_UP).toDouble())
                }
                item.copy(desempenho6Meses = serie)
            }
        }
        return ranking
    }
}

internal data class ProdutoRanking(
    val produtoId: Long,
    val nome: String,
    val preco: BigDecimal,
    val cmv: BigDecimal
)

internal object CalculoRanking {
    fun custoPonderado(lotes: List<Pair<Int, BigDecimal>>): BigDecimal {
        val validos = lotes.filter { (quantidadeG, _) -> quantidadeG > 0 }
        require(validos.isNotEmpty()) { "É necessário ao menos um lote com saldo positivo" }
        val quantidadeTotal = validos.sumOf { it.first.toLong() }
        return validos.sumOf { (quantidadeG, custoGrama) ->
            custoGrama.multiply(BigDecimal(quantidadeG))
        }.divide(BigDecimal(quantidadeTotal), 8, RoundingMode.HALF_UP)
    }

    fun converterParaGramas(
        quantidade: BigDecimal,
        unidade: String,
        pesoPorUnidadeG: Int?
    ): BigDecimal? {
        if (quantidade <= BigDecimal.ZERO) return null
        val fator = when (unidade.trim().lowercase()) {
            "g" -> BigDecimal.ONE
            "kg" -> BigDecimal(1000)
            "un", "l" -> pesoPorUnidadeG?.takeIf { it > 0 }?.let(::BigDecimal)
            else -> null
        } ?: return null
        return quantidade.multiply(fator)
    }

    fun calcular(
        produtos: List<ProdutoRanking>,
        vendasAtuais: Map<Long, Int>,
        vendasAnteriores: Map<Long, Int>,
        periodoAnteriorComparavel: Boolean
    ): List<RankingItemDto> {
        val atual = ordenar(produtos, vendasAtuais)
        val posicoesAnteriores = if (periodoAnteriorComparavel) {
            ordenar(produtos, vendasAnteriores).associate { it.produto.produtoId to it.posicao }
        } else emptyMap()

        return atual.map { item ->
            val margemUnitaria = item.produto.preco.subtract(item.produto.cmv)
            RankingItemDto(
                posicao = item.posicao,
                produtoId = item.produto.produtoId,
                nome = item.produto.nome,
                preco = item.produto.preco.moeda().toDouble(),
                cmv = item.produto.cmv.moeda().toDouble(),
                margemPct = margemUnitaria.multiply(BigDecimal(100))
                    .divide(item.produto.preco, 2, RoundingMode.HALF_UP).toDouble(),
                vendasUnidades = item.unidades,
                lucroBrutoTotal = item.lucroTotal.moeda().toDouble(),
                variacaoPosicao = posicoesAnteriores[item.produto.produtoId]
                    ?.takeIf { periodoAnteriorComparavel }
                    ?.minus(item.posicao),
                categoriaBcg = null
            )
        }
    }

    private fun ordenar(produtos: List<ProdutoRanking>, vendas: Map<Long, Int>): List<ItemCalculado> =
        produtos.map { produto ->
            val unidades = vendas[produto.produtoId]?.coerceAtLeast(0) ?: 0
            val lucro = produto.preco.subtract(produto.cmv).multiply(BigDecimal(unidades))
            ItemCalculado(0, produto, unidades, lucro)
        }.sortedWith(
            compareByDescending<ItemCalculado> { it.lucroTotal }
        ).mapIndexed { indice, item -> item.copy(posicao = indice + 1) }

    private fun BigDecimal.moeda(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
}

private data class ItemCalculado(
    val posicao: Int,
    val produto: ProdutoRanking,
    val unidades: Int,
    val lucroTotal: BigDecimal
)
