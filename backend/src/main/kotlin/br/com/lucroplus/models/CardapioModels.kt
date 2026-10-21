package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class MesDesempenhoDto(
    val mes: String,
    val vendasUnidades: Int,
    val lucroBrutoTotal: Double
)

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
    val desempenho6Meses: List<MesDesempenhoDto> = emptyList()
)
