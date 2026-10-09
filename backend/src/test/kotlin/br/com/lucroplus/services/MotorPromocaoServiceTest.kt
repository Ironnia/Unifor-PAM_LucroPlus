package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MotorPromocaoServiceTest {
    private val hoje = LocalDate.of(2026, 9, 25)

    @Test
    fun `sugere por lote salvo e prato sem criar promocao nem incluir lote ciente`() {
        val banco = Database.connect(
            "jdbc:h2:mem:sugestoes;MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(
                UsuariosTable, IngredientesTable, ProdutosTable, LotesTable, FichasTecnicasTable,
                VendasTable, ItensVendaTable, AlertasTable, AcoesAlertaTable, PromocoesTable
            )
            IngredientesTable.insert {
                it[id] = 1L; it[nome] = "Tomate"; it[unidade] = "kg"
                it[pesoPorUnidadeG] = 1000; it[estoqueMinimo] = BigDecimal.ZERO
            }
            IngredientesTable.insert {
                it[id] = 2L; it[nome] = "Queijo"; it[unidade] = "kg"
                it[pesoPorUnidadeG] = 1000; it[estoqueMinimo] = BigDecimal.ZERO
            }
            IngredientesTable.insert {
                it[id] = 3L; it[nome] = "Alface"; it[unidade] = "kg"
                it[pesoPorUnidadeG] = 1000; it[estoqueMinimo] = BigDecimal.ZERO
            }
            ProdutosTable.insert {
                it[id] = 10L; it[nome] = "Pizza"; it[preco] = BigDecimal("100.00")
                it[categoria] = "Pizzas"; it[ativo] = true
            }
            ProdutosTable.insert {
                it[id] = 11L; it[nome] = "Salada"; it[preco] = BigDecimal("50.00")
                it[categoria] = "Saladas"; it[ativo] = true
            }
            ProdutosTable.insert {
                it[id] = 12L; it[nome] = "Sopa"; it[preco] = BigDecimal("30.00")
                it[categoria] = "Sopas"; it[ativo] = true
            }
            fun lote(idLote: Long, ingrediente: Long, prazo: LocalDate, custo: String) {
                LotesTable.insert {
                    it[id] = idLote; it[ingredienteId] = ingrediente; it[quantidadeG] = 1000
                    it[custoUnitario] = BigDecimal(custo)
                    it[dataValidade] = prazo.toKotlinLocalDate()
                    it[dataEntrada] = hoje.minusDays(1).toKotlinLocalDate()
                }
            }
            lote(100L, 1L, hoje.plusDays(7), "0.1000")
            lote(101L, 1L, hoje.plusDays(8), "0.2000")
            lote(102L, 2L, hoje.plusDays(20), "0.0500")
            lote(103L, 1L, hoje.plusDays(7), "0.1000")
            lote(104L, 3L, hoje.plusDays(7), "0.0500")
            fun ficha(prato: Long, ingrediente: Long, quantidade: String) {
                FichasTecnicasTable.insert {
                    it[produtoId] = prato; it[ingredienteId] = ingrediente
                    it[quantidadeUsada] = BigDecimal(quantidade); it[unidade] = "kg"
                }
            }
            ficha(10L, 1L, "0.1000")
            ficha(10L, 2L, "0.0500")
            ficha(11L, 1L, "0.0500")
            ficha(12L, 3L, "0.0500")
            fun acao(loteId: Long, alertaId: Long, tipo: String) {
                AlertasTable.insert {
                    it[id] = alertaId; it[AlertasTable.loteId] = loteId
                    it[AlertasTable.tipo] = "VENCIMENTO"; it[mensagem] = "Teste"
                    it[dataAlerta] = hoje.toKotlinLocalDate(); it[visualizado] = true
                }
                AcoesAlertaTable.insert {
                    it[AcoesAlertaTable.loteId] = loteId
                    it[AcoesAlertaTable.alertaId] = alertaId
                    it[acao] = tipo; it[dataAcao] = hoje.toKotlinLocalDate()
                }
            }
            acao(100L, 200L, "SALVAR_LOTE")
            acao(101L, 201L, "CIENTE")
            AlertasTable.insert {
                it[id] = 203L; it[loteId] = 103L; it[tipo] = "VENCIMENTO"
                it[mensagem] = "Teste"; it[dataAlerta] = hoje.toKotlinLocalDate()
                it[visualizado] = false
            }
            AlertasTable.insert {
                it[id] = 204L; it[loteId] = 104L; it[tipo] = "VENCIMENTO"
                it[mensagem] = "Teste"; it[dataAlerta] = hoje.toKotlinLocalDate()
                it[visualizado] = false
            }
            VendasTable.insert {
                it[id] = 300L; it[dataVenda] = hoje.minusDays(1).toKotlinLocalDate()
                it[valorTotal] = BigDecimal("200.00"); it[origem] = "manual"
            }
            ItensVendaTable.insert {
                it[id] = 400L; it[vendaId] = 300L; it[produtoId] = 10L
                it[quantidade] = 2; it[precoUnitario] = BigDecimal("100.00")
            }
        }

        val sugestoes = runBlocking { MotorPromocaoService.listarSugestoes(hoje) }
        assertEquals(2, sugestoes.size)
        assertEquals(setOf(10L, 11L), sugestoes.map { it.pratoId }.toSet())
        assertFalse(sugestoes.any { it.loteId == 101L })
        val pizza = sugestoes.single { it.pratoId == 10L }
        assertEquals(100L, pizza.loteId)
        assertEquals("CRITICO", pizza.criticidade)
        assertEquals(2, pizza.vendas30Dias)
        assertEquals(87.5, pizza.margemPct)
        assertEquals(87, pizza.limiteDescontoMargemBrutaPct)
        assertEquals(100.0, pizza.precoVenda)
        assertEquals(87.5, pizza.lucroBrutoEstimado)
        assertEquals(hoje.plusDays(6).toString(), pizza.dataSugerida)
        assertEquals(hoje.plusDays(6).toString(), pizza.prazoLimiteVenda)
        assertEquals(6, pizza.diasParaPrazoLimite)
        assertEquals(null, sugestoes.single { it.pratoId == 11L }.dataSugerida)
        val previas = runBlocking { MotorPromocaoService.listarPrevias(hoje) }
        assertEquals(1, previas.size)
        assertEquals(103L, previas.single().loteId)
        assertEquals(10L, previas.single().pratoId)
        assertEquals(hoje.plusDays(6).toString(), previas.single().dataSugerida)
        transaction(banco) {
            ItensVendaTable.insert {
                it[id] = 401L; it[vendaId] = 300L; it[produtoId] = 11L
                it[quantidade] = 2; it[precoUnitario] = BigDecimal("50.00")
            }
        }
        assertEquals(10L, runBlocking { MotorPromocaoService.listarPrevias(hoje) }.single().pratoId)
        transaction(banco) {
            ItensVendaTable.update({ ItensVendaTable.id eq 401L }) { it[quantidade] = 3 }
        }
        assertEquals(11L, runBlocking { MotorPromocaoService.listarPrevias(hoje) }.single().pratoId)
        transaction(banco) { assertEquals(0L, PromocoesTable.selectAll().count()) }
    }
}
