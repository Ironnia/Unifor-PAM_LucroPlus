package br.com.lucroplus.services

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object RegraValidadeLote {
    fun prazoLimiteVenda(dataValidade: LocalDate): LocalDate = dataValidade.minusDays(1)
    fun validadeMinima(hoje: LocalDate): LocalDate = hoje.plusDays(1)
    fun validadeMaxima(hoje: LocalDate): LocalDate = hoje.plusDays(15)

    fun diasAtePrazo(dataValidade: LocalDate, hoje: LocalDate): Int =
        ChronoUnit.DAYS.between(hoje, prazoLimiteVenda(dataValidade)).toInt()

    fun criticidade(diasAtePrazo: Int): String = when {
        diasAtePrazo <= 7 -> "CRITICO"
        diasAtePrazo <= 14 -> "ATENCAO"
        else -> "SAUDAVEL"
    }
}
