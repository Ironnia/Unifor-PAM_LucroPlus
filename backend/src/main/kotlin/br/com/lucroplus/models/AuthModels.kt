package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val senha: String
)

@Serializable
data class RegisterRequest(
    val nome: String,
    val email: String,
    val senha: String,
    val tipo: String = "GERENTE"
)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val expiresIn: Long? = null,
    val id: Long? = null,
    val nome: String,
    val email: String,
    val tipo: String
)

@Serializable
data class UsuarioDto(
    val id: Long,
    val nome: String,
    val email: String,
    val tipo: String,
    val ativo: Boolean = true
)