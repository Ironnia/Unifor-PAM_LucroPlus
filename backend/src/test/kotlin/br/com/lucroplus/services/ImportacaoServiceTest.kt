package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImportacaoServiceTest {
    @Test
    fun `massa MySQL da demonstracao sincroniza sem duplicar`() = runBlocking {
        assumeTrue(System.getenv("LUCROPLUS_DEMO_MYSQL") == "true", "Teste de demonstração exige base isolada preparada")
        val banco = Database.connect(System.getenv("DB_URL"), "com.mysql.cj.jdbc.Driver",
            System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        TransactionManager.defaultDatabase = banco
        val antes = transaction(banco) { VendasTable.selectAll().where { VendasTable.restauranteId eq 1L }.count() }
        val servico = ImportacaoService(1)
        val primeira = servico.sincronizarPdv()
        assertTrue(primeira.sucesso, primeira.erros.toString())
        assertEquals(38, primeira.vendasNovas)
        assertTrue(primeira.movimentosNovos > 0)
        val segunda = servico.sincronizarPdv()
        assertTrue(segunda.sucesso, segunda.erros.toString())
        assertEquals(0, segunda.vendasNovas)
        assertEquals(0, segunda.movimentosNovos)
        transaction(banco) {
            assertEquals(antes + 38, VendasTable.selectAll().where { VendasTable.restauranteId eq 1L }.count())
        }
        TransactionManager.closeAndUnregister(banco)
    }

    @Test
    fun `mock transacional grava vendas e baixas uma vez por loja`() = runBlocking {
        val banco = Database.connect("jdbc:h2:mem:demo_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        TransactionManager.defaultDatabase = banco
        val hoje = LocalDate.now()
        transaction(banco) {
            SchemaUtils.create(RestaurantesTable, UsuariosTable, ProdutosTable, IngredientesTable, FichasTecnicasTable,
                LotesTable, VendasTable, ItensVendaTable, PromocoesTable, MovimentosEstoqueTable,
                ConfiguracoesRestauranteTable, SyncPdvHistoricoTable)
            RestaurantesTable.insert { it[id] = 1; it[nome] = "Loja demo" }
            ProdutosTable.insert {
                it[id] = 1; it[restauranteId] = 1; it[idExterno] = "LAN-01"
                it[nome] = "Prato de teste"; it[preco] = BigDecimal("20.00"); it[categoria] = "Teste"
            }
            IngredientesTable.insert {
                it[id] = 1; it[restauranteId] = 1; it[idExterno] = "ING-01"
                it[nome] = "Ingrediente"; it[unidade] = "g"
            }
            FichasTecnicasTable.insert {
                it[produtoId] = 1; it[ingredienteId] = 1
                it[quantidadeUsada] = BigDecimal("10"); it[unidade] = "g"
            }
            LotesTable.insert {
                it[id] = 1; it[restauranteId] = 1; it[ingredienteId] = 1
                it[quantidadeG] = 2000; it[quantidadeInicialG] = 2000
                it[custoUnitario] = BigDecimal("0.01")
                it[dataEntrada] = hoje.minusDays(1).toKotlinLocalDate()
                it[entradaEm] = InstantesEstoque.parse("${hoje.minusDays(1)}T08:00:00-03:00")
                it[dataValidade] = hoje.plusDays(2).toKotlinLocalDate()
            }
            ConfiguracoesRestauranteTable.insert {
                it[restauranteId] = 1; it[chave] = "pdv_url"; it[valor] = "mock://pdv"
            }
        }

        val servico = ImportacaoService(1)
        val relogio = Clock.fixed(hoje.atTime(23, 59).atZone(DashboardService.FUSO_FORTALEZA).toInstant(),
            DashboardService.FUSO_FORTALEZA)
        val dashboard = DashboardService(1, relogio)
        assertEquals(listOf(0, 0), dashboard.obterKpis().ultimosSeteDias.takeLast(2).map { it.quantidadePedidos })
        val primeira = servico.sincronizarPdv()
        assertTrue(primeira.sucesso, primeira.erros.toString())
        assertEquals(38, primeira.totalLinhas)
        assertEquals(38, primeira.vendasNovas)
        assertEquals(38, primeira.movimentosNovos)
        assertNotNull(primeira.ultimaSincronizacao)
        val atualizado = dashboard.obterKpis()
        assertEquals(listOf(16, 22), atualizado.ultimosSeteDias.takeLast(2).map { it.quantidadePedidos })
        assertEquals(38, atualizado.totalPedidos)
        assertTrue(atualizado.faturamentoTotal > 0)
        transaction(banco) {
            assertEquals(38L, VendasTable.selectAll().count())
            assertEquals(38L, MovimentosEstoqueTable.selectAll().count())
            val unidades = ItensVendaTable.selectAll().sumOf { it[ItensVendaTable.quantidade] }
            assertEquals(2000 - unidades * 10, LotesTable.selectAll().single()[LotesTable.quantidadeG])
        }

        val segunda = servico.sincronizarPdv()
        assertTrue(segunda.sucesso, segunda.erros.toString())
        assertEquals(0, segunda.vendasNovas)
        assertEquals(0, segunda.movimentosNovos)
        transaction(banco) {
            assertEquals(38L, VendasTable.selectAll().count())
            assertEquals(38L, MovimentosEstoqueTable.selectAll().count())
            assertEquals(2L, SyncPdvHistoricoTable.selectAll().count())
        }
        TransactionManager.closeAndUnregister(banco)
    }
}
