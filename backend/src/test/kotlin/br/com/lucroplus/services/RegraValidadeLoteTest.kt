package br.com.lucroplus.services

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RegraValidadeLoteTest {
    private val hoje = LocalDate.of(2026, 9, 25)

    @Test
    fun `prazo e um dia antes da validade inclusive na virada de mes`() {
        val validade = hoje.plusDays(14)

        assertEquals(validade.minusDays(1), RegraValidadeLote.prazoLimiteVenda(validade))
        assertEquals(LocalDate.of(2026, 9, 30), RegraValidadeLote.prazoLimiteVenda(LocalDate.of(2026, 10, 1)))
        assertEquals(13, RegraValidadeLote.diasAtePrazo(validade, hoje))
    }

    @Test
    fun `classifica os limites dos tres niveis`() {
        val casos = mapOf(
            -1 to "CRITICO",
            0 to "CRITICO",
            7 to "CRITICO",
            8 to "ATENCAO",
            14 to "ATENCAO",
            15 to "SAUDAVEL"
        )

        casos.forEach { (dias, esperado) ->
            assertEquals(esperado, RegraValidadeLote.criticidade(dias), "dias=$dias")
        }
    }
}
