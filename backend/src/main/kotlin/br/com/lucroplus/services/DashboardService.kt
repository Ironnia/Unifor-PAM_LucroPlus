package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.*
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class DashboardService(private val restauranteId: Long) {

    suspend fun obterKpis(): DashboardKpisDto = dbQuery {
        val dataInicioKmp = LocalDate.now().minusDays(30).toKotlinLocalDate()

        val vendas = VendasTable
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and (VendasTable.dataVenda greaterEq dataInicioKmp) }
            .toList()

        val totalPedidos = vendas.size
        val faturamentoTotal = vendas.sumOf { it[VendasTable.valorTotal].toDouble() }.roundTo(2)

        val totalProdutosVendidos = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and (VendasTable.dataVenda greaterEq dataInicioKmp) }
            .sumOf { it[ItensVendaTable.quantidade] }

        val ticketMedio = if (totalPedidos > 0) {
            (faturamentoTotal / totalPedidos).roundTo(2)
        } else {
            0.0
        }

        DashboardKpisDto(
            faturamentoTotal = faturamentoTotal,
            totalPedidos = totalPedidos,
            totalProdutosVendidos = totalProdutosVendidos,
            ticketMedio = ticketMedio
        )
    }

    suspend fun obterDadosGraficos(): DashboardVendasResponse = dbQuery {
        val faturamentoDiario = obterFaturamentoDiarioInterno(30)
        val top5 = obterTop5MaisVendidosInterno()
        val baixoGiro = obterProdutosBaixoGiroInterno()

        DashboardVendasResponse(
            faturamentoDiario = faturamentoDiario,
            top5Produtos = top5,
            produtosBaixoGiro = baixoGiro
        )
    }

    suspend fun obterPrevisaoDemanda(): List<PrevisaoDemandaDto> = dbQuery {
        val hojeJava = LocalDate.now()
        val dataInicioKmp = hojeJava.minusDays(28).toKotlinLocalDate()
        val dataFimKmp = hojeJava.minusDays(1).toKotlinLocalDate()

        val itensVendaPeriodo = (ItensVendaTable innerJoin VendasTable innerJoin ProdutosTable)
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and (ProdutosTable.restauranteId eq restauranteId) and (VendasTable.dataVenda greaterEq dataInicioKmp) and (VendasTable.dataVenda lessEq dataFimKmp) }
            .toList()

        val produtosAtivos = ProdutosTable
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .map { it[ProdutosTable.id] to it[ProdutosTable.nome] }

        val resultado = mutableListOf<PrevisaoDemandaDto>()

        val agrupado = itensVendaPeriodo
            .groupBy {
                val dataJava = LocalDate.parse(it[VendasTable.dataVenda].toString())
                val diaMysql = (dataJava.dayOfWeek.value % 7) + 1
                val prodId = it[ProdutosTable.id]
                prodId to diaMysql
            }
            .mapValues { (_, itens) ->
                itens.sumOf { it[ItensVendaTable.quantidade] }
            }

        for ((prodId, prodNome) in produtosAtivos) {
            for (dia in 1..7) {
                val totalVendido = agrupado[prodId to dia] ?: 0
                val mediaEsperada = (totalVendido / 4.0).roundTo(1)

                resultado.add(
                    PrevisaoDemandaDto(
                        produtoId = prodId,
                        produtoNome = prodNome,
                        diaSemana = dia,
                        totalVendidoPeriodo = totalVendido,
                        mediaVendasEsperada = mediaEsperada
                    )
                )
            }
        }

        resultado.sortedWith(compareBy({ it.produtoNome }, { it.diaSemana }))
    }

    private fun obterFaturamentoDiarioInterno(dias: Long): List<FaturamentoDiarioDto> {
        val dataInicioKmp = LocalDate.now().minusDays(dias).toKotlinLocalDate()

        val vendas = VendasTable
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and (VendasTable.dataVenda greaterEq dataInicioKmp) }
            .toList()

        return vendas
            .groupBy { it[VendasTable.dataVenda].toString() }
            .map { (data, vendasDoDia) ->
                FaturamentoDiarioDto(
                    dataVenda = data,
                    faturamento = vendasDoDia.sumOf { it[VendasTable.valorTotal].toDouble() }.roundTo(2),
                    totalPedidos = vendasDoDia.size
                )
            }
            .sortedBy { it.dataVenda }
    }

    private fun obterTop5MaisVendidosInterno(): List<ProdutoRankingDto> {
        val itens = (ItensVendaTable innerJoin ProdutosTable)
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .toList()

        return itens
            .groupBy { it[ProdutosTable.id] to it[ProdutosTable.nome] }
            .map { (key, itensDoProd) ->
                val (id, nome) = key
                val totalVendido = itensDoProd.sumOf { it[ItensVendaTable.quantidade] }
                val receitaTotal = itensDoProd.sumOf {
                    it[ItensVendaTable.quantidade] * it[ItensVendaTable.precoUnitario].toDouble()
                }.roundTo(2)

                ProdutoRankingDto(
                    id = id,
                    nome = nome,
                    totalVendido = totalVendido,
                    receitaTotal = receitaTotal
                )
            }
            .sortedByDescending { it.totalVendido }
            .take(5)
    }

    private fun obterProdutosBaixoGiroInterno(): List<ProdutoRankingDto> {
        val dataLimiteKmp = LocalDate.now().minusDays(15).toKotlinLocalDate()
        val todosProdutos = ProdutosTable
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .map { it[ProdutosTable.id] to it[ProdutosTable.nome] }

        val itensRecentes = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and (VendasTable.dataVenda greaterEq dataLimiteKmp) }
            .toList()
            .groupBy { it[ItensVendaTable.produtoId] }

        return todosProdutos
            .map { (id, nome) ->
                val vendasProd = itensRecentes[id] ?: emptyList()
                val totalVendido = vendasProd.sumOf { it[ItensVendaTable.quantidade] }
                val receitaTotal = vendasProd.sumOf {
                    it[ItensVendaTable.quantidade] * it[ItensVendaTable.precoUnitario].toDouble()
                }.roundTo(2)

                ProdutoRankingDto(
                    id = id,
                    nome = nome,
                    totalVendido = totalVendido,
                    receitaTotal = receitaTotal
                )
            }
            .sortedBy { it.totalVendido }
            .take(5)
    }

    private fun Double.roundTo(decimals: Int): Double {
        return BigDecimal(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }
}
