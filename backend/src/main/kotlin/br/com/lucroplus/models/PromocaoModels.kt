package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
enum class StatusPromocao { SUGERIDA, EM_ANDAMENTO, CONCLUIDA_SUCESSO, EXPIRADA_COM_PERDA, RECUSADA }

@Serializable
data class CriarPromocaoRequest(val loteId: Long, val pratoId: Long)

@Serializable
data class AtivarPromocaoRequest(val descontoPct: Int, val dataInicio: String, val dataFim: String)

@Serializable
data class ConfirmarPromocaoRequest(val loteEscoado: Boolean)

@Serializable
data class PromocaoDto(
    val id: Long,
    val loteId: Long,
    val pratoId: Long,
    val pratoNome: String,
    val status: StatusPromocao,
    val descontoPct: Int?,
    val dataSugestao: String,
    val dataAtivacao: String?,
    val dataInicio: String?,
    val dataFim: String?,
    val dataConfirmacao: String?,
    val quantidadeReferenciaG: Int?,
    val valorEmRisco: String?,
    val valorSalvo: String,
    val aguardaConfirmacao: Boolean,
    val ativadaEm: String? = null,
    val inicioEfetivo: String? = null,
    val fimExclusivo: String? = null,
    val custoReferenciaPorGrama: String? = null,
    val quantidadeConsumidaG: Int? = null,
    val quantidadeDescartadaG: Int? = null,
    val valorPerdido: String? = null,
    val confirmadaEm: String? = null,
    val confirmadaPor: Long? = null,
    val ingredienteNome: String? = null,
    val numeroLote: String? = null,
    val prazoLimiteVenda: String? = null,
    val criticidade: String? = null
)

@Serializable
data class ResumoPromocoesDto(
    val emAndamento: Long,
    val prejuizoEvitado: String,
    val valorSalvoMes: Double = 0.0,
    val valorEmRiscoAtivo: Double = 0.0,
    val pendentes: Int = 0
)
