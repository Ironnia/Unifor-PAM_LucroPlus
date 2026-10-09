package br.com.lucroplus.services

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculoSugestaoPromocaoTest {
    private val hoje = LocalDate.of(2026, 9, 25)

    @Test
    fun `usa 30 dias inclusivos e escolhe dia disponivel antes do prazo`() {
        val vendas = listOf(
            hoje.minusDays(30) to 100, // fora da janela
            hoje.minusDays(29) to 2,   // dentro da janela
            hoje.minusDays(28) to 1,
            hoje.minusDays(1) to 5
        )
        assertEquals(hoje.plusDays(6), CalculoSugestaoPromocao.melhorDia(vendas, hoje, hoje.plusDays(7)))
        assertEquals(hoje, CalculoSugestaoPromocao.melhorDia(vendas, hoje, hoje.plusDays(1)))
    }

    @Test
    fun `sem historico ou prazo encerrado nao inventa melhor dia`() {
        assertNull(CalculoSugestaoPromocao.melhorDia(emptyList(), hoje, hoje.plusDays(7)))
        assertNull(CalculoSugestaoPromocao.melhorDia(listOf(hoje to 3), hoje, hoje.minusDays(1)))
    }

    @Test
    fun `converte ficha tecnica para gramas e calcula margem sem arredondar custo cedo`() {
        val custo = CalculoSugestaoPromocao.custoItem(
            BigDecimal("0.1800"), "kg", 1000, BigDecimal("0.0325")
        )
        assertEquals(BigDecimal("5.85000000"), custo)
        assertEquals(BigDecimal("79.76"), CalculoSugestaoPromocao.margemPct(BigDecimal("28.90"), custo!!))
        assertEquals(BigDecimal("1.2000"), CalculoSugestaoPromocao.custoItem(
            BigDecimal.ONE, "un", 80, BigDecimal("0.0150")
        ))
        assertNull(CalculoSugestaoPromocao.custoItem(BigDecimal.ONE, "caixa", 80, BigDecimal.ONE))
        assertNull(CalculoSugestaoPromocao.margemPct(BigDecimal.ZERO, BigDecimal.ONE))
    }

    @Test
    fun `desconto inteiro preserva lucro bruto positivo apos arredondar centavos`() {
        assertEquals(19, CalculoSugestaoPromocao.limiteDescontoPositivo(
            BigDecimal("10.00"), BigDecimal("8.00")
        ))
        assertNull(CalculoSugestaoPromocao.limiteDescontoPositivo(
            BigDecimal("10.00"), BigDecimal("10.00")
        ))
    }
}
