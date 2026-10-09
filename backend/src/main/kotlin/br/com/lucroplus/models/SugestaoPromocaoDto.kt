package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class SugestaoPromocaoDto(
    val loteId: Long,
    val numeroLote: String,
    val ingredienteNome: String,
    val pratoId: Long,
    val pratoNome: String,
    val prazoLimiteVenda: String,
    val diasParaPrazoLimite: Int,
    val criticidade: String,
    val melhorDiaSemana: String?,
    val dataSugerida: String?,
    val vendas30Dias: Int,
    val precoVenda: Double?,
    val lucroBrutoEstimado: Double?,
    val margemPct: Double?,
    val limiteDescontoMargemBrutaPct: Int?,
    val observacao: String?
)

@Serializable
data class PreviaPromocaoDto(
    val loteId: Long,
    val pratoId: Long,
    val pratoNome: String,
    val melhorDia: String?,
    val dataSugerida: String?
)
