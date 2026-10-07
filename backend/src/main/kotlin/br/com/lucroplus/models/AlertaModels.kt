package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class IngredienteResumoDto(
    val nome: String,
    val unidade: String? = null
)

@Serializable
data class LoteResumoDto(
    val id: Long? = null,
    val quantidade: Double,
    val custoUnitario: Double,
    val ingrediente: IngredienteResumoDto? = null
)

@Serializable
data class AlertaDto(
    val id: Long,
    val loteId: Long? = null,
    val tipo: String = "VENCIMENTO",
    val mensagem: String,
    val dataAlerta: String,
    val visualizado: Boolean = false,
    val prazoLimiteVenda: String,
    val diasParaPrazoLimite: Int,
    val criticidade: String,
    val lote: LoteResumoDto? = null
)
