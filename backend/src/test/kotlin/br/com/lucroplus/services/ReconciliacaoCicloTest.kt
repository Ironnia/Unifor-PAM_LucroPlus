package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.models.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class ReconciliacaoCicloTest {
    @Test
    fun `validade amanha admite hoje e validade hoje nao admite nova campanha`() = runBlocking {
        val f = PromocaoFixture()
        f.lote(10, 1); f.lote(11, 0)
        val p = f.servico.criar(CriarPromocaoRequest(10, 1))
        assertEquals(400, assertFailsWith<ErroPromocao> {
            f.servico.ativar(p.id, AtivarPromocaoRequest(20, f.hoje.toString(), f.hoje.plusDays(1).toString()))
        }.codigo)
        assertEquals(StatusPromocao.EM_ANDAMENTO, f.servico.ativar(p.id,
            AtivarPromocaoRequest(20, f.hoje.toString(), f.hoje.toString())).status)
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.criar(CriarPromocaoRequest(11, 1)) }.codigo)
    }

    @Test
    fun `janela de quatorze dias usa prazo e aceita fim na validade menos um`() = runBlocking {
        val f = PromocaoFixture()
        f.lote(10, 15)
        val p = f.servico.criar(CriarPromocaoRequest(10, 1))
        assertEquals(400, assertFailsWith<ErroPromocao> {
            f.servico.ativar(p.id, AtivarPromocaoRequest(20, f.hoje.toString(), f.hoje.plusDays(15).toString()))
        }.codigo)
        val ativa = f.servico.ativar(p.id, AtivarPromocaoRequest(99,
            f.hoje.plusDays(14).toString(), f.hoje.plusDays(14).toString()))
        assertEquals(99, ativa.descontoPct)
    }

    @Test
    fun `descartar lote sem ficha nao cria campanha e nao reabre alerta`() = runBlocking {
        val f = PromocaoFixture()
        transaction(f.banco) { FichasTecnicasTable.deleteAll() }
        assertTrue(MotorPromocaoService.listarSugestoes(f.hoje).isEmpty())
        repeat(2) { f.servico.descartarPendente(1) }
        assertTrue(f.servico.listar().isEmpty())
        assertFalse(AlertaService.listarLotesPendentes().any { it.loteId == 1L })
        assertEquals(ResultadoAcaoAlerta.CONFLITO, AlertaService.executarAcao(1, AcaoAlerta.SALVAR_LOTE))
        transaction(f.banco) {
            assertTrue(AlertasTable.selectAll().where { AlertasTable.id eq 1L }.single()[AlertasTable.visualizado])
            assertEquals(1234, LotesTable.selectAll().where { LotesTable.id eq 1L }.single()[LotesTable.quantidadeG])
        }
    }

    @Test
    fun `descarte disputa criacao sem apagar campanha criada`() = runBlocking {
        val f = PromocaoFixture()
        val resultados = listOf(
            async(Dispatchers.Default) { try { f.servico.descartarPendente(1); 200 } catch (e: ErroPromocao) { e.codigo } },
            async(Dispatchers.Default) { try { f.servico.criar(CriarPromocaoRequest(1, 1)); 200 } catch (e: ErroPromocao) { e.codigo } }
        ).awaitAll()
        assertEquals(listOf(200, 409), resultados.sorted())
        val campanhas = f.servico.listar()
        if (campanhas.isNotEmpty()) assertEquals(StatusPromocao.SUGERIDA, campanhas.single().status)
        else assertFalse(AlertaService.listarLotesPendentes().any { it.loteId == 1L })
    }

    @Test
    fun `rascunho sobrevive recarga mas sai dos candidatos e ativacao sai dos pendentes`() = runBlocking {
        val f = PromocaoFixture()
        val p = f.servico.criar(CriarPromocaoRequest(1, 1))
        assertTrue(AlertaService.listarLotesPendentes().any { it.loteId == 1L })
        assertEquals(p, f.servico.listar().single())
        assertFalse(MotorPromocaoService.listarSugestoes(f.hoje).any { it.loteId == 1L })
        f.servico.ativar(p.id, AtivarPromocaoRequest(20, f.hoje.toString(), f.hoje.toString()))
        assertFalse(AlertaService.listarLotesPendentes().any { it.loteId == 1L })
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.descartarPendente(1) }.codigo)
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.recusar(p.id) }.codigo)
    }
}
