package br.com.lucroplus.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {

    private val secret = System.getenv("JWT_SECRET")
        ?.takeIf { it.length >= 32 }
        ?: error("JWT_SECRET não configurada ou menor que 32 caracteres.")

    const val ISSUER = "lucroplus-backend"
    const val AUDIENCE = "LucroPlus-users"
    const val REALM = "LucroPlus Access"

    const val VALIDITY_IN_MS = 8L * 60 * 60 * 1000

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(AUDIENCE)
        .withIssuer(ISSUER)
        .build()

    fun generateToken(
        id: Long,
        email: String,
        tipo: String,
        nome: String,
        restauranteId: Long? = null
    ): String {
        val token = JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("id", id)
            .withClaim("email", email)
            .withClaim("tipo", tipo)
            .withClaim("nome", nome)
        if (restauranteId != null) token.withClaim("restaurante_id", restauranteId)
        return token
            .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_IN_MS))
            .sign(algorithm)
    }
}
