package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.*

class AvisosSugestaoServiceTest {
    private fun fixture() = PromocaoFixture(java.time.LocalDate.of(2026, 9, 26))
    private fun PromocaoFixture.insumoSemLote() = transaction(banco) {
        IngredientesTable.insert {
                it[IngredientesTable.restauranteId] = 1L; it[id] = 10L; it[nome] = "Alface"; it[unidade] = "kg" }
        FichasTecnicasTable.insert {
            it[produtoId] = 1L; it[ingredienteId] = 10L
            it[quantidadeUsada] = BigDecimal("0.03"); it[unidade] = "kg"
        }
    }

    private fun PromocaoFixture.venda(diasAtras: Long, quantidadeVendida: Int = 2) = transaction(banco) {
        val venda = VendasTable.insert {
                it[VendasTable.restauranteId] = 1L
            it[dataVenda] = hoje.minusDays(diasAtras).toKotlinLocalDate()
            it[valorTotal] = BigDecimal("40.00"); it[origem] = "manual"
        } get VendasTable.id
        ItensVendaTable.insert {
            it[vendaId] = venda; it[produtoId] = 1L
            it[quantidade] = quantidadeVendida; it[precoUnitario] = BigDecimal("20.00")
        }
    }

    @Test
    fun `custo e historico ausentes geram dois avisos com ingrediente e resumo compativel`() = runBlocking {
        val f = fixture(); f.insumoSemLote()
        val s = MotorPromocaoService(1).listarSugestoes(f.hoje).single { it.loteId == 1L && it.pratoId == 1L }
        assertNull(s.margemPct); assertNull(s.dataSugerida)
        assertEquals(setOf("SEM_CUSTO_ELEGIVEL", "SEM_VENDAS_RECENTES"), s.avisos.map { it.codigo }.toSet())
        val custo = s.avisos.single { it.campo == "MARGEM" }
        assertEquals(10L, custo.ingredienteId); assertEquals("Alface", custo.ingredienteNome)
        assertTrue(custo.mensagem.contains("Alface"))
        s.avisos.forEach { assertTrue(s.observacao!!.contains(it.mensagem)) }
        transaction(f.banco) { assertEquals(0, PromocoesTable.selectAll().count().toInt()) }
    }

    @Test
    fun `historico existente mas incompativel nao e tratado como ausencia de vendas`() = runBlocking {
        val f = fixture(); f.venda(1)
        transaction(f.banco) {
            LotesTable.update({ LotesTable.id eq 1L }) { it[dataValidade] = f.hoje.plusDays(1).toKotlinLocalDate() }
        }
        val s = MotorPromocaoService(1).listarSugestoes(f.hoje).single { it.loteId == 1L && it.pratoId == 1L }
        assertEquals(2, s.vendas30Dias); assertNull(s.dataSugerida)
        assertEquals(93.85, s.margemPct)
        assertEquals(listOf("SEM_DIA_COMPATIVEL"), s.avisos.map { it.codigo })
    }

    @Test
    fun `vendas fora da janela futuras ou nao positivas nao justificam melhor dia`() = runBlocking {
        val f = fixture(); f.venda(30); f.venda(-1); f.venda(0, 0)
        val s = MotorPromocaoService(1).listarSugestoes(f.hoje).single { it.loteId == 1L && it.pratoId == 1L }
        assertNull(s.dataSugerida)
        assertEquals(listOf("SEM_VENDAS_RECENTES"), s.avisos.map { it.codigo })
    }

    @Test
    fun `venda no limite de 30 dias calcula dia mesmo quando falta custo`() = runBlocking {
        val f = fixture(); f.insumoSemLote(); f.venda(29)
        val s = MotorPromocaoService(1).listarSugestoes(f.hoje).single { it.loteId == 1L && it.pratoId == 1L }
        assertEquals(f.hoje.plusDays(6).toString(), s.dataSugerida)
        assertNull(s.margemPct)
        assertEquals(listOf("SEM_CUSTO_ELEGIVEL"), s.avisos.map { it.codigo })
    }

    @Test
    fun `preco invalido nao e confundido com custo ausente`() = runBlocking {
        val f = fixture(); f.venda(0)
        transaction(f.banco) { ProdutosTable.update({ ProdutosTable.id eq 1L }) { it[preco] = BigDecimal.ZERO } }
        val s = MotorPromocaoService(1).listarSugestoes(f.hoje).single { it.loteId == 1L && it.pratoId == 1L }
        assertNull(s.margemPct); assertNull(s.limiteDescontoMargemBrutaPct)
        assertEquals(listOf("PRECO_INVALIDO"), s.avisos.map { it.codigo })
    }

    @Test
    fun `margens zero negativa e custo zero continuam valores conhecidos sem avisos`() = runBlocking {
        val f = fixture(); f.venda(0)
        for ((custo, margem) in listOf("0.2000" to 0.0, "0.3000" to -50.0, "0.0000" to 100.0)) {
            transaction(f.banco) { LotesTable.update({ LotesTable.id eq 1L }) { it[custoUnitario] = BigDecimal(custo) } }
            val s = MotorPromocaoService(1).listarSugestoes(f.hoje).single { it.loteId == 1L && it.pratoId == 1L }
            assertEquals(margem, s.margemPct)
            assertTrue(s.avisos.isEmpty()); assertNull(s.observacao)
            assertEquals(if (margem > 0) 100 else null, s.limiteDescontoMargemBrutaPct)
        }
    }

    @Test
    fun `diagnostico identifica todas as falhas de quantidade custo unidade e peso`() {
        fun motivos(qtd: String, unidade: String, peso: Int?, custo: String?) =
            DiagnosticoSugestaoPromocao.custoItem(7L, "Pão", BigDecimal(qtd), unidade, peso, custo?.let(::BigDecimal))
        val simultaneos = motivos("0", "x", null, null)
        assertEquals(setOf("QUANTIDADE_INVALIDA", "SEM_CUSTO_ELEGIVEL", "UNIDADE_INVALIDA"), simultaneos.map { it.codigo }.toSet())
        assertTrue(simultaneos.all { it.ingredienteId == 7L && it.ingredienteNome == "Pão" })
        for (peso in listOf(null, 0, -1)) assertEquals(listOf("PESO_UNIDADE_INVALIDO"), motivos("1", "un", peso, "1").map { it.codigo })
        assertEquals(listOf("CUSTO_INVALIDO"), motivos("1", "g", null, "-1").map { it.codigo })
        for (unidade in listOf("g", "kg", "l", "UN")) assertTrue(motivos("1", unidade, 80, "0").isEmpty())
    }
}
