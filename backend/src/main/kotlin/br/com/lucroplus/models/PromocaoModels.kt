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

// valores monetarios como string decimal para evitar perda de precisao
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
    val aguardaConfirmacao: Boolean
)

@Serializable
data class ResumoPromocoesDto(val emAndamento: Long, val prejuizoEvitado: String)
