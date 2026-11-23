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
    val margemPct: Double?,
    val limiteDescontoMargemBrutaPct: Int?,
    val observacao: String?,
    val avisos: List<AvisoSugestaoDto> = emptyList(),
    val precoVenda: Double? = null,
    val lucroBrutoEstimado: Double? = null
)

@Serializable
data class AvisoSugestaoDto(
    val campo: String,
    val codigo: String,
    val mensagem: String,
    val ingredienteId: Long? = null,
    val ingredienteNome: String? = null
)
