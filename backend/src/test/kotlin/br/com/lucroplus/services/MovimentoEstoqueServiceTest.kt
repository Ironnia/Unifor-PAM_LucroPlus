package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class MovimentoEstoqueServiceTest {
    @Test fun `distribuicao usa entrada depois ID e nao altera a lista recebida`() {
        val antes = InstantesEstoque.parse("2026-09-30T08:00:00-03:00")
        val depois = InstantesEstoque.parse("2026-09-30T09:00:00-03:00")
        val lotes = listOf(LoteFifo(9, 500, depois), LoteFifo(2, 1000, antes), LoteFifo(1, 500, antes))
        assertEquals(listOf(BaixaFifo(1, 500), BaixaFifo(2, 1000), BaixaFifo(9, 100)), DistribuicaoFifo.distribuir(1600, lotes))
        assertEquals(500, lotes.first().saldoG)
        assertFailsWith<ErroEstoque> { DistribuicaoFifo.distribuir(2001, lotes) }
    }

    @Test fun `dois pratos consomem 7000 g sem campanha e conservam inicial`() = runBlocking {
        FifoFixture().use { f ->
            f.lote(); f.venda(quantidade = 20)
            transaction(f.banco) {
                ProdutosTable.insert { it[id] = 3; it[restauranteId] = 1; it[nome] = "Prato 100g"; it[preco] = BigDecimal("20"); it[categoria] = "Teste" }
                FichasTecnicasTable.insert { it[produtoId] = 3; it[ingredienteId] = 1; it[quantidadeUsada] = BigDecimal("100"); it[unidade] = "g" }
            }
            f.venda(2, 30, "2026-10-01T11:00:00-03:00", produto = 3)
            assertEquals(4000, MovimentoEstoqueService(1).processarVenda(1).sumOf { it.quantidadeG })
            assertEquals(3000, f.consumir(2).sumOf { it.quantidadeG }); assertEquals(3000, f.saldo())
            transaction(f.banco) {
                assertEquals(10000, LotesTable.selectAll().single()[LotesTable.quantidadeInicialG])
                assertEquals(0, PromocoesTable.selectAll().count().toInt())
            }
        }
    }

    @Test fun `travessia usa lote recebido antes mesmo com validade posterior e desempata ID`() {
        FifoFixture().use { f ->
            f.lote(2, 1000, validade = "2026-10-04")
            f.lote(1, 1000, validade = "2026-10-09")
            f.venda(quantidade = 10)
            assertEquals(listOf(1L to 1000, 2L to 1000), f.consumir().map { it.loteId to it.quantidadeG })
            assertEquals(0, f.saldo(1)); assertEquals(0, f.saldo(2)); assertEquals(2, f.movimentos())
        }
    }

    @Test fun `lote futuro vencido e legado sem horario nao sao usados e prazo comum e respeitado`() {
        FifoFixture().use { f ->
            f.lote(1, entrada = "2026-10-01T10:01:00-03:00")
            f.lote(2, validade = "2026-09-30"); f.lote(3, validade = "2026-10-01")
            f.lote(4)
            transaction(f.banco) { LotesTable.update({ LotesTable.id eq 4 }) { it[entradaEm] = null } }
            f.lote(5, validade = "2026-10-02")
            f.venda()
            assertEquals(5L, f.consumir().single().loteId)
            assertEquals(9800, f.saldo(5)); for (id in 1L..4L) assertEquals(10000, f.saldo(id))
        }
    }

    @Test fun `falta de segundo ingrediente nao grava baixa do primeiro`() {
        FifoFixture().use { f ->
            f.lote(); f.venda()
            transaction(f.banco) {
                IngredientesTable.insert { it[id] = 3; it[restauranteId] = 1; it[nome] = "Sem estoque"; it[unidade] = "g" }
                FichasTecnicasTable.insert { it[produtoId] = 1; it[ingredienteId] = 3; it[quantidadeUsada] = BigDecimal("1"); it[unidade] = "g" }
            }
            assertFailsWith<ErroEstoque> { f.consumir() }
            assertEquals(10000, f.saldo()); assertEquals(0, f.movimentos())
        }
    }

    @Test fun `falha depois do FIFO desfaz venda movimentos e saldo na transacao do importador`() {
        FifoFixture().use { f ->
            f.lote()
            assertFailsWith<IllegalStateException> {
                transaction(f.banco) {
                    f.venda(); MovimentoEstoqueService(1).consumirVendaNaTransacao(1)
                    error("Falha deliberada depois da baixa")
                }
            }
            assertEquals(10000, f.saldo()); assertEquals(0, f.movimentos())
            transaction(f.banco) { assertEquals(0, VendasTable.selectAll().count().toInt()) }
        }
    }

    @Test fun `repetir venda devolve movimento original mesmo depois de mudar receita`() {
        FifoFixture().use { f ->
            f.lote(); f.venda()
            val primeiro = f.consumir()
            transaction(f.banco) { FichasTecnicasTable.update({ FichasTecnicasTable.produtoId eq 1 }) { it[quantidadeUsada] = BigDecimal("900") } }
            assertEquals(primeiro, f.consumir()); assertEquals(9800, f.saldo()); assertEquals(1, f.movimentos())
        }
    }

    @Test fun `itens do mesmo pedido compartilham lote sem perder consumo`() {
        FifoFixture().use { f ->
            f.lote(); f.venda(); f.item(2, 1, 1, 2)
            assertEquals(listOf(200, 400), f.consumir().map { it.quantidadeG })
            assertEquals(9400, f.saldo()); assertEquals(2, f.movimentos())
        }
    }

    @Test fun `lojas com mesma origem externa nao compartilham saldo e venda alheia e negada`() {
        FifoFixture().use { f ->
            f.lote(); f.lote(2, loja = 2); f.venda(); f.venda(2, loja = 2)
            transaction(f.banco) { VendasTable.update({ VendasTable.id eq 2 }) { it[idExterno] = "v1" } }
            assertFailsWith<ErroEstoque> { f.consumir(2, 1) }
            f.consumir(); assertEquals(10000, f.saldo(2))
            f.consumir(2, 2); assertEquals(9800, f.saldo(1)); assertEquals(9800, f.saldo(2))
        }
    }

    @Test fun `produto e ingrediente de outra loja sao recusados sem escrita`() {
        FifoFixture().use { f ->
            f.lote(); f.venda(produto = 2)
            assertFailsWith<ErroEstoque> { f.consumir() }
            transaction(f.banco) {
                ItensVendaTable.update({ ItensVendaTable.id eq 1 }) { it[produtoId] = 1 }
                FichasTecnicasTable.deleteWhere { produtoId eq 1 }
                FichasTecnicasTable.insert { it[produtoId] = 1; it[ingredienteId] = 2; it[quantidadeUsada] = BigDecimal("100"); it[unidade] = "g" }
            }
            assertFailsWith<ErroEstoque> { f.consumir() }
            assertEquals(10000, f.saldo()); assertEquals(0, f.movimentos())
        }
    }

    @Test fun `conversoes kg UN L sao multiplicadas pelo numero de porcoes`() {
        FifoFixture().use { f ->
            f.lote(); f.venda(quantidade = 2)
            for ((unidade, porcao, peso, gramas) in listOf(
                listOf("kg", "0.2", "1000", "400"), listOf("un", "1", "80", "160"), listOf("L", "0.01", "900", "18"))) {
                transaction(f.banco) {
                    IngredientesTable.update({ IngredientesTable.id eq 1 }) { it[pesoPorUnidadeG] = peso.toInt() }
                    FichasTecnicasTable.update({ FichasTecnicasTable.produtoId eq 1 }) { it[FichasTecnicasTable.unidade] = unidade; it[quantidadeUsada] = BigDecimal(porcao) }
                }
                val id = when (unidade) { "kg" -> 2L; "un" -> 3L; else -> 4L }
                f.venda(id, 2)
                assertEquals(gramas.toInt(), f.consumir(id).single().quantidadeG)
            }
            assertEquals(9422, f.saldo())
        }
    }

    @Test fun `grama fracionaria overflow receita vazia e quantidade invalida nao baixam`() {
        FifoFixture().use { f ->
            f.lote(); f.venda()
            for ((porcao, quantidade) in listOf("0.5" to 1, "200" to Int.MAX_VALUE, "200" to 0, "200" to -1)) {
                transaction(f.banco) {
                    FichasTecnicasTable.update({ FichasTecnicasTable.produtoId eq 1 }) { it[quantidadeUsada] = BigDecimal(porcao) }
                    ItensVendaTable.update({ ItensVendaTable.id eq 1 }) { it[ItensVendaTable.quantidade] = quantidade }
                }
                assertFailsWith<IllegalArgumentException> { f.consumir() }
            }
            transaction(f.banco) {
                ItensVendaTable.update({ ItensVendaTable.id eq 1 }) { it[quantidade] = 1 }
                FichasTecnicasTable.deleteWhere { produtoId eq 1 }
            }
            assertFailsWith<ErroEstoque> { f.consumir() }
            assertEquals(0, f.movimentos()); assertEquals(10000, f.saldo())
        }
    }

    @Test fun `venda sem instante nao ganha meia noite inventada`() {
        FifoFixture().use { f ->
            f.lote(); f.venda()
            transaction(f.banco) { VendasTable.update({ VendasTable.id eq 1 }) { it[dataVendaEm] = null } }
            assertFailsWith<ErroEstoque> { f.consumir() }; assertEquals(0, f.movimentos())
        }
    }

    @Test fun `consumo guarda instante original na virada UTC e recusa replay retroativo novo`() {
        FifoFixture().use { f ->
            f.lote(); f.venda(horario = "2026-10-02T02:30:00Z")
            assertEquals("2026-10-01T23:30-03:00", InstantesEstoque.iso(f.consumir().single().ocorridoEm))
            f.venda(2, horario = "2026-10-01T10:00:00-03:00")
            assertFailsWith<ErroEstoque> { f.consumir(2) }; assertEquals(9800, f.saldo())
            assertEquals(1, f.movimentos())
        }
    }

    private fun concorrentes(f: FifoFixture, vendas: List<Long>): List<List<ConsumoEstoque>> {
        val barreira = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            pool.invokeAll(vendas.map { venda -> Callable { barreira.await(10, TimeUnit.SECONDS); f.consumir(venda) } }, 30, TimeUnit.SECONDS)
                .map { it.get(20, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
    }

    @Test fun `duas chamadas simultaneas da mesma venda fazem uma unica baixa`() {
        FifoFixture().use { f ->
            f.lote(); f.venda()
            val resultados = concorrentes(f, listOf(1, 1))
            assertEquals(resultados[0], resultados[1]); assertEquals(9800, f.saldo()); assertEquals(1, f.movimentos())
        }
    }

    @Test fun `vendas simultaneas diferentes nao perdem atualizacao de saldo`() {
        FifoFixture().use { f ->
            f.lote(); f.venda(); f.venda(2)
            assertEquals(400, concorrentes(f, listOf(1, 2)).flatten().sumOf { it.quantidadeG })
            assertEquals(9600, f.saldo()); assertEquals(2, f.movimentos())
        }
    }

    @Test fun `duas vendas disputando saldo limitado permitem uma baixa e recusam a outra`() {
        FifoFixture().use { f ->
            f.lote(gramas = 200); f.venda(); f.venda(2)
            val barreira = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val resultados = pool.invokeAll((1L..2L).map { venda -> Callable {
                    barreira.await(10, TimeUnit.SECONDS)
                    try { f.consumir(venda); true } catch (_: ErroEstoque) { false }
                } }, 30, TimeUnit.SECONDS).map { it.get() }
                assertEquals(listOf(false, true), resultados.sorted())
                assertEquals(0, f.saldo()); assertEquals(1, f.movimentos())
            } finally { pool.shutdownNow() }
        }
    }

    private fun conferirMassa(cenario: String) {
        assumeTrue(System.getenv("LUCROPLUS_FIFO_MYSQL") == "true", "Massa SQL FIFO requer MySQL real")
        FifoFixture(cenario).use { f ->
            val esperado = Json.parseToJsonElement(File("../database/fifo_resultados_esperados.json").readText())
                .jsonObject.getValue("scenarios").jsonObject.getValue(cenario).jsonObject
            val vendas = transaction(f.banco) {
                VendasTable.selectAll().orderBy(VendasTable.dataVendaEm to SortOrder.ASC,
                    VendasTable.idExterno to SortOrder.ASC, VendasTable.id to SortOrder.ASC)
                    .map { it[VendasTable.id] to it[VendasTable.restauranteId]!! }
            }
            val movimentos = vendas.flatMap { (venda, loja) -> f.consumir(venda, loja) }
            val obtidos = movimentos.map { listOf(it.itemVendaId.toString(), it.loteId.toString(), it.quantidadeG.toString(), it.ocorridoEm.toString()) }.sortedBy { it.joinToString("|") }
            val previstos = esperado.getValue("movements").jsonArray.map { elemento ->
                val m = elemento.jsonObject
                listOf(m.getValue("sale").jsonPrimitive.content, m.getValue("lot").jsonPrimitive.content,
                    m.getValue("grams").jsonPrimitive.content,
                    kotlinx.datetime.Instant.parse(m.getValue("at").jsonPrimitive.content.replace(' ', 'T') + "Z").toString())
            }.sortedBy { it.joinToString("|") }
            assertEquals(previstos, obtidos, "Movimentos $cenario")
            transaction(f.banco) {
                val lotes = LotesTable.selectAll().toList()
                assertEquals(esperado.getValue("expected_balances").jsonObject.mapValues { it.value.jsonPrimitive.int },
                    lotes.associate { it[LotesTable.id].toString() to it[LotesTable.quantidadeG] })
                assertTrue(lotes.all { it[LotesTable.quantidadeG] >= 0 })
                for (lote in lotes) assertEquals(lote[LotesTable.quantidadeInicialG]!!,
                    lote[LotesTable.quantidadeG] + movimentos.filter { it.loteId == lote[LotesTable.id] }.sumOf { it.quantidadeG })
                assertEquals(0, PromocoesTable.selectAll().count().toInt())
            }
            val vendaPorItem = transaction(f.banco) { ItensVendaTable.selectAll().associate { it[ItensVendaTable.id] to it[ItensVendaTable.vendaId] } }
            val porVenda = movimentos.groupBy { vendaPorItem.getValue(it.itemVendaId) }
            for ((venda, loja) in vendas.reversed()) assertEquals(porVenda.getValue(venda), f.consumir(venda, loja))
            assertEquals(previstos.size, f.movimentos())
            f.carregar("homologacao_fifo_lucroplus.sql")
            transaction(f.banco) {
                assertEquals(esperado.getValue("expected_balances").jsonObject.mapValues { it.value.jsonPrimitive.int },
                    LotesTable.selectAll().associate { it[LotesTable.id].toString() to it[LotesTable.quantidadeG] })
            }
            println("FIFO_MYSQL cenario=$cenario vendas=${vendas.size} movimentos=${movimentos.size} replay=sem_duplicacao")
        }
    }

    @Test fun `massa FIFO parcial`() = conferirMassa("parcial")
    @Test fun `massa FIFO integral`() = conferirMassa("integral")
    @Test fun `massa FIFO perda`() = conferirMassa("perda")
    @Test fun `massa FIFO travessia`() = conferirMassa("travessia")
    @Test fun `massa FIFO inicio efetivo`() = conferirMassa("inicio_efetivo")
    @Test fun `massa FIFO inicio futuro`() = conferirMassa("inicio_futuro")
    @Test fun `massa FIFO historico`() = conferirMassa("historico")
}
