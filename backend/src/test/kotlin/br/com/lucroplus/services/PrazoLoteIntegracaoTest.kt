package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

class PrazoLoteIntegracaoTest {
    @Test
    fun `lotes e alertas concordam nas bordas e repeticao nao duplica`() = runBlocking {
        val banco = Database.connect("jdbc:h2:mem:prazo35;MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = banco
        val hoje = LocalDate.now()
        val dias = listOf(-1, 0, 7, 8, 14, 15)
        transaction(banco) {
            SchemaUtils.create(IngredientesTable, LotesTable, AlertasTable)
            IngredientesTable.insert {
                it[id] = 1L; it[nome] = "Tomate"; it[unidade] = "g"
            }
            dias.forEachIndexed { i, prazo ->
                LotesTable.insert {
                    it[id] = i + 1L; it[ingredienteId] = 1L; it[quantidadeG] = 1000
                    it[custoUnitario] = BigDecimal("0.0065")
                    it[dataValidade] = hoje.plusDays(prazo + 1L).toKotlinLocalDate()
                    it[dataEntrada] = hoje.toKotlinLocalDate()
                }
            }
            LotesTable.insert {
                it[id] = 7L; it[ingredienteId] = 1L; it[quantidadeG] = 0
                it[custoUnitario] = BigDecimal("0.0065")
                it[dataValidade] = hoje.plusDays(2).toKotlinLocalDate()
                it[dataEntrada] = hoje.toKotlinLocalDate()
            }
        }
        val lotes = LoteService.listarLotes()
        assertEquals(dias, lotes.map { it.diasParaPrazoLimite })
        assertEquals(listOf("CRITICO", "CRITICO", "CRITICO", "ATENCAO", "ATENCAO", "SAUDAVEL"), lotes.map { it.criticidade })
        lotes.forEach { assertEquals(LocalDate.parse(it.dataValidade).minusDays(1).toString(), it.prazoLimiteVenda) }
        val alertas = AlertaService.obterAlertasVencimento()
        assertEquals(listOf(0, 7, 8, 14), alertas.map { it.diasParaPrazoLimite })
        alertas.forEach { alerta ->
            val lote = lotes.single { it.id == alerta.loteId }
            assertEquals(lote.prazoLimiteVenda, alerta.prazoLimiteVenda)
            assertEquals(lote.criticidade, alerta.criticidade)
            assertTrue(alerta.mensagem.contains("prazo limite de venda"))
        }
        assertEquals(alertas.map { it.id }, AlertaService.obterAlertasVencimento().map { it.id })
        transaction(banco) { assertEquals(4L, AlertasTable.selectAll().count()) }
    }
}

