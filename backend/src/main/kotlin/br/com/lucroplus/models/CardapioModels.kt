package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class MesDesempenhoDto(
    val mes: String,
    val vendasUnidades: Int,
    val lucroBrutoTotal: Double
)

/**
 * Item exibido no ranking da Engenharia de Cardápio.
 *
 * O lucro é bruto e estimado apenas com o custo dos ingredientes. Ele não inclui
 * despesas como aluguel, energia ou folha de pagamento, portanto não representa
 * o lucro líquido do restaurante.
 */
@Serializable
data class RankingItemDto(
    val posicao: Int,
    val produtoId: Long,
    val nome: String,
    val preco: Double,
    val cmv: Double,
    val margemPct: Double,
    val vendasUnidades: Int,
    val lucroBrutoTotal: Double,
    val variacaoPosicao: Int?,
    val categoriaBcg: String? = null,
    val desempenho6Meses: List<MesDesempenhoDto> = emptyList(),
    val historicoBcg: List<BcgHistoricoItem> = emptyList()
)
