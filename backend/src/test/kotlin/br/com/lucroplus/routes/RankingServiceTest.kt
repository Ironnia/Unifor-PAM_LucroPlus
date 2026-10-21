package br.com.lucroplus.routes

import br.com.lucroplus.services.CalculoRanking
import br.com.lucroplus.services.ProdutoRanking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RankingServiceTest {
    @Test
    fun `pondera custo por grama pelo saldo dos lotes`() {
        val custo = CalculoRanking.custoPonderado(listOf(
            900 to BigDecimal("0.010"),
            100 to BigDecimal("0.030")
        ))

        assertEquals(BigDecimal("0.01200000"), custo)
    }

    @Test
    fun `calcula cmv em gramas margem e lucro bruto total`() {
        val quantidadeCarneG = CalculoRanking.converterParaGramas(BigDecimal("0.150"), "kg", null)!!
        val quantidadePaoG = CalculoRanking.converterParaGramas(BigDecimal.ONE, "un", 80)!!
        val cmv = quantidadeCarneG.multiply(BigDecimal("0.040"))
            .add(quantidadePaoG.multiply(BigDecimal("0.025")))

        val ranking = CalculoRanking.calcular(
            listOf(ProdutoRanking(1, "Burger", BigDecimal("20.00"), cmv)),
            vendasAtuais = mapOf(1L to 10),
            vendasAnteriores = emptyMap(),
            periodoAnteriorComparavel = false
        ).single()

        assertEquals(8.0, ranking.cmv)
        assertEquals(60.0, ranking.margemPct)
        assertEquals(120.0, ranking.lucroBrutoTotal)
    }

    @Test
    fun `ordena estritamente pelo lucro total em reais`() {
        val produtos = listOf(
            ProdutoRanking(1, "Margem alta", BigDecimal("20"), BigDecimal("2")),
            ProdutoRanking(2, "Maior lucro total", BigDecimal("12"), BigDecimal("8")),
            ProdutoRanking(3, "Maior volume isolado", BigDecimal("10"), BigDecimal("9"))
        )

        val ranking = CalculoRanking.calcular(
            produtos,
            vendasAtuais = mapOf(1L to 2, 2L to 10, 3L to 30),
            vendasAnteriores = emptyMap(),
            periodoAnteriorComparavel = false
        )

        assertEquals(listOf(2L, 1L, 3L), ranking.map { it.produtoId })
        assertEquals(listOf(40.0, 36.0, 30.0), ranking.map { it.lucroBrutoTotal })
    }

    @Test
    fun `variacao e posicao anterior menos posicao atual`() {
        val produtos = listOf(
            ProdutoRanking(1, "Subiu", BigDecimal("10"), BigDecimal("5")),
            ProdutoRanking(2, "Caiu", BigDecimal("10"), BigDecimal("5")),
            ProdutoRanking(3, "Manteve", BigDecimal("10"), BigDecimal("5"))
        )

        val ranking = CalculoRanking.calcular(
            produtos,
            vendasAtuais = mapOf(1L to 30, 3L to 20, 2L to 10),
            vendasAnteriores = mapOf(2L to 30, 3L to 20, 1L to 10),
            periodoAnteriorComparavel = true
        )

        assertEquals(listOf(2, 0, -2), ranking.map { it.variacaoPosicao })
    }

    @Test
    fun `sem dia correspondente retorna variacao nula`() {
        val ranking = CalculoRanking.calcular(
            listOf(ProdutoRanking(1, "Prato", BigDecimal.TEN, BigDecimal.ONE)),
            vendasAtuais = mapOf(1L to 1),
            vendasAnteriores = mapOf(1L to 99),
            periodoAnteriorComparavel = false
        ).single()

        assertNull(ranking.variacaoPosicao)
    }
}
