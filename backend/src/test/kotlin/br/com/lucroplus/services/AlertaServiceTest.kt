package br.com.lucroplus.services

import br.com.lucroplus.database.AcoesAlertaTable
import br.com.lucroplus.database.AlertasTable
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.database.PromocoesTable
import br.com.lucroplus.database.ProdutosTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertaServiceTest {
    private fun novoBanco() {

        val banco = Database.connect(
            "jdbc:h2:mem:alertas_${java.util.UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.createMissingTablesAndColumns(
                br.com.lucroplus.database.RestaurantesTable, br.com.lucroplus.database.UsuariosTable,
                IngredientesTable, LotesTable, AlertasTable, AcoesAlertaTable, ProdutosTable, PromocoesTable
            )
            br.com.lucroplus.routes.prepararContaTeste()
            PromocoesTable.deleteAll()
            AcoesAlertaTable.deleteAll()
            AlertasTable.deleteAll()
            LotesTable.deleteAll()
            IngredientesTable.deleteAll()
        }
    }

    private fun criarLote(diasAteValidade: Long, quantidade: Int = 1000): Long = transaction {
        val ingredienteId = IngredientesTable.insert {
                it[IngredientesTable.restauranteId] = 1L
            it[nome] = "Tomate"
            it[unidade] = "g"
            it[pesoPorUnidadeG] = 1
            it[estoqueMinimo] = BigDecimal.ZERO
        }[IngredientesTable.id]
        LotesTable.insert {
                it[LotesTable.restauranteId] = 1L
            it[LotesTable.ingredienteId] = ingredienteId
            it[quantidadeG] = quantidade
            it[custoUnitario] = BigDecimal("0.0100")
            it[dataValidade] = LocalDate.now().plusDays(diasAteValidade + 1).toKotlinLocalDate()
            it[dataEntrada] = LocalDate.now().toKotlinLocalDate()
            it[numeroLote] = "TESTE-$diasAteValidade"
        }[LotesTable.id]
    }

    @Test
    fun `salvar lote remove alerta e inclui uma unica entrada na fila`() = runBlocking {
        novoBanco()
        val loteId = criarLote(7)
        val alerta = AlertaService(1).obterAlertasVencimento().single()
        assertEquals(loteId, alerta.loteId)
        assertEquals("CRITICO", alerta.criticidade)

        assertEquals(ResultadoAcaoAlerta.CONCLUIDA,
            AlertaService(1).executarAcao(alerta.id, AcaoAlerta.SALVAR_LOTE))
        assertTrue(AlertaService(1).obterAlertasVencimento().isEmpty())
        assertEquals(loteId, AlertaService(1).listarLotesPendentes().single().loteId)
        assertEquals(ResultadoAcaoAlerta.JA_APLICADA,
            AlertaService(1).executarAcao(alerta.id, AcaoAlerta.SALVAR_LOTE))
        assertEquals(ResultadoAcaoAlerta.CONFLITO,
            AlertaService(1).executarAcao(alerta.id, AcaoAlerta.CIENTE))
        assertEquals(1, AlertaService(1).listarLotesPendentes().size)
    }

    @Test
    fun `ciente descarta alerta sem criar promocao pendente`() = runBlocking {
        novoBanco()
        criarLote(14)
        val alerta = AlertaService(1).obterAlertasVencimento().single()
        assertEquals("ATENCAO", alerta.criticidade)
        assertEquals(ResultadoAcaoAlerta.CONCLUIDA,
            AlertaService(1).executarAcao(alerta.id, AcaoAlerta.CIENTE))
        assertTrue(AlertaService(1).obterAlertasVencimento().isEmpty())
        assertTrue(AlertaService(1).listarLotesPendentes().isEmpty())
    }

    @Test
    fun `somente lotes ativos dentro da janela geram alerta`() = runBlocking {
        novoBanco()
        criarLote(15)
        criarLote(-1)
        criarLote(5, quantidade = 0)
        val valido = criarLote(8)

        val alertas = AlertaService(1).obterAlertasVencimento()
        assertEquals(listOf(valido), alertas.map { it.loteId })
    }
}
