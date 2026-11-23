package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class LoteItemDto(
    val id: Long,
    val ingredienteId: Long,
    val ingredienteNome: String,
    val unidade: String,
    val quantidadeG: Int,
    val quantidadeFormatada: String,
    val custoUnitario: Double,
    val dataValidade: String,
    val dataEntrada: String,
    val numeroLote: String,
    val observacao: String? = null,
    val diasRestantes: Int,
    val prazoLimiteVenda: String,
    val diasParaPrazoLimite: Int,
    val criticidade: String,
    val quantidadeInicialG: Int? = null,
    val quantidadeRestanteG: Int = quantidadeG,
    val custoPorGrama: String? = null,
    val origem: String? = null,
    val idExterno: String? = null,
    val compraIdExterno: String? = null,
    val entradaEm: String? = null
)

@Serializable
data class CompraHistoricoDto(
    val loteId: Long,
    val numeroLote: String,
    val dataEntrada: String,
    val mesReferencia: String,
    val quantidadeCompradaG: Long,
    val quantidadeFormatada: String,
    val precoPorKg: Double,
    val custoTotal: Double,
    val emEstoque: Boolean,
    val quantidadeRestanteG: Long = 0
)

@Serializable
data class HistoricoComprasIngredienteResponse(
    val ingredienteId: Long,
    val ingredienteNome: String,
    val unidade: String,
    val consumoMedioMensalG: Double,
    val compras: List<CompraHistoricoDto>
)
