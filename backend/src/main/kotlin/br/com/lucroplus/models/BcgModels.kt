package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class BcgHistoricoItem(val mesReferencia: String, val categoria: String)

@Serializable
data class BcgPratoDto(
    val produtoId: Long,
    val nome: String,
    val preco: Double,
    val cmv: Double,
    val margemPct: Double,
    val vendas30Dias: Int,
    val lucro30Dias: Double,
    val categoriaBcg: String,
    val historicoBcg: List<BcgHistoricoItem> = emptyList()
)

@Serializable
data class BcgResponse(
    val mediaVolume: Double,
    val mediaMargemPct: Double,
    val totalPratos: Int,
    val pratos: List<BcgPratoDto>
)

@Serializable
data class BcgFechamentoRequest(val mesReferencia: String)

@Serializable
data class BcgFechamentoResponse(
    val mesReferencia: String,
    val totalPratos: Int,
    val atualizado: Boolean
)
