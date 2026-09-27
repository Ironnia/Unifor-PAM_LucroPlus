package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class PdvConfigDto(
    val url: String? = null,
    val host: String? = null,
    val porta: Int? = null,
    val username: String? = null,
    val usuario: String? = null,
    val password: String? = null,
    val senha: String? = null,
    val banco: String? = null
)

@Serializable
data class TestarConexaoResponse(
    val sucesso: Boolean,
    val tempoRespostaMs: Long = 0L,
    val mensagem: String,
    val detalhes: String? = null
)
