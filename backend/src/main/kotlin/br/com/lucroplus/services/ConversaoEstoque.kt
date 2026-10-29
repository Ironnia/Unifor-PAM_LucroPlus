package br.com.lucroplus.services

import kotlinx.datetime.Instant as InstanteKotlin
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

object ConversaoEstoque {
    fun gramasPorUnidade(unidade: String, pesoPorUnidadeG: Int?): Int = when (unidade.trim().lowercase()) {
        "g" -> 1
        "kg" -> 1000
        "un", "l" -> requireNotNull(pesoPorUnidadeG?.takeIf { it > 0 }) {
            "UN/L exigem peso_por_unidade_g explícito e positivo"
        }
        else -> throw IllegalArgumentException("Unidade inválida; use g, kg, un ou L")
    }

    fun quantidadeG(quantidade: BigDecimal, unidade: String, pesoPorUnidadeG: Int?): Int {
        require(quantidade > BigDecimal.ZERO) { "Quantidade deve ser positiva" }
        val gramas = quantidade.multiply(BigDecimal(gramasPorUnidade(unidade, pesoPorUnidadeG)))
        val inteiro = try { gramas.intValueExact() } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Quantidade deve resultar em gramas inteiras dentro do limite INTEGER")
        }
        require(inteiro > 0) { "Quantidade deve resultar em pelo menos 1 g" }
        return inteiro
    }

    fun custoPorGrama(precoUnidade: BigDecimal, unidade: String, pesoPorUnidadeG: Int?): BigDecimal {
        require(precoUnidade >= BigDecimal.ZERO) { "Custo não pode ser negativo" }
        return precoUnidade.divide(BigDecimal(gramasPorUnidade(unidade, pesoPorUnidadeG)), 8, RoundingMode.HALF_UP)
    }
}

object InstantesEstoque {
    private val fuso = ZoneId.of("America/Fortaleza")

    fun parse(valor: String): InstanteKotlin {
        val instante = try { OffsetDateTime.parse(valor).toInstant() }
        catch (_: java.time.format.DateTimeParseException) { LocalDateTime.parse(valor).atZone(fuso).toInstant() }
        return InstanteKotlin.parse(instante.toString())
    }

    fun agora(): InstanteKotlin = InstanteKotlin.parse(Instant.now()
        .truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString())

    fun iso(valor: InstanteKotlin): String = Instant.parse(valor.toString())
        .atZone(fuso).toOffsetDateTime().toString()

    fun dataCivil(valor: InstanteKotlin): LocalDate = OffsetDateTime.parse(iso(valor)).toLocalDate()
}
