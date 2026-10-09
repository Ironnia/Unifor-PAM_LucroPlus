package br.com.lucroplus.services

import br.com.lucroplus.database.PromocoesTable
import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.database.AcoesAlertaTable
import br.com.lucroplus.database.AlertasTable
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.LotesTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertaServiceTest {
    private fun novoBanco() {
        val banco = Database.connect(
            "jdbc:h2:mem:alertas_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.createMissingTablesAndColumns(
                IngredientesTable, ProdutosTable, LotesTable, AlertasTable, AcoesAlertaTable, PromocoesTable
            )
            PromocoesTable.deleteAll()
            AcoesAlertaTable.deleteAll()
            AlertasTable.deleteAll()
            LotesTable.deleteAll()
            IngredientesTable.deleteAll()
        }
    }

    private fun criarLote(diasAteValidade: Long, quantidade: Int = 1000): Long = transaction {
        val ingredienteId = IngredientesTable.insert {
            it[nome] = "Tomate"
            it[unidade] = "g"
            it[pesoPorUnidadeG] = 1
            it[estoqueMinimo] = BigDecimal.ZERO
        }[IngredientesTable.id]
        LotesTable.insert {
            it[LotesTable.ingredienteId] = ingredienteId
            it[quantidadeG] = quantidade
            it[custoUnitario] = BigDecimal("0.0100")
            it[dataValidade] = LocalDate.now().plusDays(diasAteValidade).toKotlinLocalDate()
            it[dataEntrada] = LocalDate.now().toKotlinLocalDate()
            it[numeroLote] = "TESTE-$diasAteValidade"
        }[LotesTable.id]
    }

    @Test
    fun `salvar lote remove alerta e inclui uma unica entrada na fila`() = runBlocking {
        novoBanco()
        val loteId = criarLote(7)
        val alerta = AlertaService.obterAlertasVencimento().single()
        assertEquals(loteId, alerta.loteId)
        assertEquals("CRITICO", alerta.criticidade)
        assertEquals(LocalDate.now().plusDays(6).toString(), alerta.prazoLimiteVenda)

        assertEquals(ResultadoAcaoAlerta.CONCLUIDA,
            AlertaService.executarAcao(alerta.id, AcaoAlerta.SALVAR_LOTE))
        assertTrue(AlertaService.obterAlertasVencimento().isEmpty())
        assertEquals(loteId, AlertaService.listarLotesPendentes().single().loteId)
        assertEquals(ResultadoAcaoAlerta.JA_APLICADA,
            AlertaService.executarAcao(alerta.id, AcaoAlerta.SALVAR_LOTE))
        assertEquals(ResultadoAcaoAlerta.CONFLITO,
            AlertaService.executarAcao(alerta.id, AcaoAlerta.CIENTE))
        assertEquals(1, AlertaService.listarLotesPendentes().size)
    }

    @Test
    fun `ciente descarta alerta sem criar promocao pendente`() = runBlocking {
        novoBanco()
        criarLote(15)
        val alerta = AlertaService.obterAlertasVencimento().single()
        assertEquals("ATENCAO", alerta.criticidade)
        assertEquals(ResultadoAcaoAlerta.CONCLUIDA,
            AlertaService.executarAcao(alerta.id, AcaoAlerta.CIENTE))
        assertTrue(AlertaService.obterAlertasVencimento().isEmpty())
        assertTrue(AlertaService.listarLotesPendentes().isEmpty())
    }

    @Test
    fun `somente lotes ativos dentro da janela geram alerta`() = runBlocking {
        novoBanco()
        criarLote(16)
        criarLote(0)
        criarLote(5, quantidade = 0)
        val valido = criarLote(8)

        val alertas = AlertaService.obterAlertasVencimento()
        assertEquals(listOf(valido), alertas.map { it.loteId })
    }
}
