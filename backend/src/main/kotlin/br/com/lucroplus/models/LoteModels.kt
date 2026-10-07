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
    val criticidade: String // "CRITICO", "ATENCAO", "SAUDAVEL"
)
