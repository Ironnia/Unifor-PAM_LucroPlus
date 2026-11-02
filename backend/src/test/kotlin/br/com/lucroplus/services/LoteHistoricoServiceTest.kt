package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoteHistoricoServiceTest {
    @Test
    fun `historico usa quantidade inicial custo e vendas da propria loja`() = runBlocking {
        val banco = Database.connect("jdbc:h2:mem:lotes_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(RestaurantesTable, UsuariosTable, ProdutosTable, IngredientesTable,
                LotesTable, FichasTecnicasTable, VendasTable, ItensVendaTable)
            for (loja in 1L..2L) {
                RestaurantesTable.insert { it[id] = loja; it[nome] = "Loja $loja" }
                IngredientesTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[nome] = "Queijo $loja"
                    it[unidade] = "kg"; it[pesoPorUnidadeG] = 1000
                }
                ProdutosTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[nome] = "Misto $loja"
                    it[preco] = BigDecimal("20.00"); it[categoria] = "Lanches"
                }
                FichasTecnicasTable.insert {
                    it[produtoId] = loja; it[ingredienteId] = loja
                    it[quantidadeUsada] = BigDecimal("100"); it[unidade] = "g"
                }
                LotesTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[ingredienteId] = loja
                    it[quantidadeInicialG] = 5000; it[quantidadeG] = 1000
                    it[custoUnitario] = BigDecimal("0.04"); it[numeroLote] = "40$loja"
                    it[dataEntrada] = LocalDate.of(2026, 9, 15).toKotlinLocalDate()
                    it[dataValidade] = LocalDate.of(2026, 11, 15).toKotlinLocalDate()
                }
                VendasTable.insert {
                    it[id] = loja; it[restauranteId] = loja
                    it[dataVenda] = LocalDate.of(2026, 9, 20).toKotlinLocalDate()
                    it[valorTotal] = BigDecimal("60.00")
                }
                ItensVendaTable.insert {
                    it[id] = loja; it[vendaId] = loja; it[produtoId] = loja
                    it[quantidade] = 3; it[precoUnitario] = BigDecimal("20.00")
                }
            }
        }
        val hoje = LocalDate.of(2026, 10, 16)
        val historico = LoteService(1).historicoCompras(1, hoje)!!
        assertEquals(100.0, historico.consumoMedioMensalG)
        assertEquals(5000L, historico.compras.single().quantidadeCompradaG)
        assertEquals("5.00 kg", historico.compras.single().quantidadeFormatada)
        assertEquals(40.0, historico.compras.single().precoPorKg)
        assertEquals(200.0, historico.compras.single().custoTotal)
        assertEquals(true, historico.compras.single().emEstoque)
        assertNull(LoteService(1).historicoCompras(2, hoje))
    }
}
