package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class DashboardKpisDto(
    val faturamentoTotal: Double,
    val totalPedidos: Int,
    val totalProdutosVendidos: Int,
    val ticketMedio: Double,
    val impactoFinanceiro: ImpactoFinanceiroDto,
    val variacoes: VariacoesDashboardDto,
    val periodoAtual: PeriodoDashboardDto,
    val periodoComparativo: PeriodoDashboardDto?,
    val ultimosSeteDias: List<SerieDiariaDashboardDto>
)

@Serializable
data class ImpactoFinanceiroDto(
    val valorSalvo: Double,
    val valorPerdido: Double,
    val impactoLiquido: Double,
    val economiaGeradaPct: Double
)

@Serializable
data class VariacoesDashboardDto(
    val faturamentoPct: Double?,
    val totalPedidosPct: Double?,
    val ticketMedioPct: Double?,
    val impactoLiquidoPct: Double?
)

@Serializable
data class PeriodoDashboardDto(
    val inicio: String,
    val fim: String
)

@Serializable
data class SerieDiariaDashboardDto(
    val data: String,
    val totalVendas: Double,
    val quantidadePedidos: Int
)

@Serializable
data class FaturamentoDiarioDto(
    val dataVenda: String,
    val faturamento: Double,
    val totalPedidos: Int
)

@Serializable
data class ProdutoRankingDto(
    val id: Long,
    val nome: String,
    val totalVendido: Int,
    val receitaTotal: Double
)

@Serializable
data class DashboardVendasResponse(
    val faturamentoDiario: List<FaturamentoDiarioDto>,
    val top5Produtos: List<ProdutoRankingDto>,
    val produtosBaixoGiro: List<ProdutoRankingDto>
)

@Serializable
data class PrevisaoDemandaDto(
    val produtoId: Long,
    val produtoNome: String,
    val diaSemana: Int,
    val totalVendidoPeriodo: Int,
    val mediaVendasEsperada: Double
)
