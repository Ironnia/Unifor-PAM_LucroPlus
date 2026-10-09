package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.models.*
import kotlinx.coroutines.*
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.*

class CicloPromocaoServiceTest {
    private fun PromocaoFixture.ativacao() = AtivarPromocaoRequest(20, hoje.toString(), hoje.toString())

    @Test
    fun `sucesso registra snapshot somente apos periodo e nao duplica valor`() = runBlocking {
        val f = PromocaoFixture()
        val p = f.servico.criar(CriarPromocaoRequest(1, 1))
        assertEquals(StatusPromocao.SUGERIDA, p.status)
        assertNull(p.descontoPct)
        assertEquals(p.id, f.servico.criar(CriarPromocaoRequest(1, 1)).id)
        assertEquals("0.00", f.servico.resumo().prejuizoEvitado)
        val ativa = f.servico.ativar(p.id, f.ativacao())
        assertEquals("15.18", ativa.valorEmRisco)
        assertEquals("0.00", ativa.valorSalvo)
        assertEquals(ativa, f.servico.ativar(p.id, f.ativacao()))
        assertEquals(1, f.servico.listar(StatusPromocao.EM_ANDAMENTO).size)
        assertEquals(409, assertFailsWith<ErroPromocao> {
            f.servico.confirmar(p.id, ConfirmarPromocaoRequest(true))
        }.codigo)
        transaction(f.banco) {
            LotesTable.update({ LotesTable.id eq 1L }) { it[quantidadeG] = 0; it[custoUnitario] = BigDecimal("0.50") }
        }
        f.hoje = f.hoje.plusDays(1)
        assertTrue(f.servico.listar().single().aguardaConfirmacao)
        val concluida = f.servico.confirmar(p.id, ConfirmarPromocaoRequest(true))
        assertEquals(StatusPromocao.CONCLUIDA_SUCESSO, concluida.status)
        assertEquals("15.18", concluida.valorSalvo)
        assertEquals(concluida, f.servico.confirmar(p.id, ConfirmarPromocaoRequest(true)))
        assertEquals("15.18", f.servico.resumo().prejuizoEvitado)
        assertEquals(0L, f.servico.resumo().emAndamento)
        assertEquals(409, assertFailsWith<ErroPromocao> {
            f.servico.confirmar(p.id, ConfirmarPromocaoRequest(false))
        }.codigo)
    }

    @Test
    fun `perda depende de confirmacao explicita e nunca gera valor salvo`() = runBlocking {
        val f = PromocaoFixture()
        val p = f.servico.criar(CriarPromocaoRequest(1, 1))
        f.servico.ativar(p.id, f.ativacao())
        f.hoje = f.hoje.plusDays(10)
        assertEquals(StatusPromocao.EM_ANDAMENTO, f.servico.listar().single().status)
        assertEquals("0.00", f.servico.resumo().prejuizoEvitado)
        val perda = f.servico.confirmar(p.id, ConfirmarPromocaoRequest(false))
        assertEquals(StatusPromocao.EXPIRADA_COM_PERDA, perda.status)
        assertEquals(perda, f.servico.confirmar(p.id, ConfirmarPromocaoRequest(false)))
        assertEquals("0.00", perda.valorSalvo)
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.ativar(p.id, f.ativacao()) }.codigo)
    }

    @Test
    fun `recusa encerra pendencia sem recomendar outro prato`() = runBlocking {
        val f = PromocaoFixture()
        val p = f.servico.criar(CriarPromocaoRequest(1, 1))
        assertFalse(MotorPromocaoService.listarSugestoes(f.hoje).any { it.loteId == 1L })
        val recusada = f.servico.recusar(p.id)
        assertEquals(recusada, f.servico.recusar(p.id))
        assertFalse(MotorPromocaoService.listarSugestoes(f.hoje).any { it.loteId == 1L })
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.ativar(p.id, f.ativacao()) }.codigo)
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.criar(CriarPromocaoRequest(1, 2)) }.codigo)
        assertFalse(AlertaService.listarLotesPendentes().any { it.loteId == 1L })

    }

    @Test
    fun `criacao rejeita lote ausente nao salvo vencido distante vazio e prato incompativel`() = runBlocking {
        val f = PromocaoFixture()
        assertEquals(404, assertFailsWith<ErroPromocao> { f.servico.criar(CriarPromocaoRequest(999, 1)) }.codigo)
        for (lote in 2L..5L) assertEquals(409, assertFailsWith<ErroPromocao> {
            f.servico.criar(CriarPromocaoRequest(lote, 1))
        }.codigo)
        assertEquals(400, assertFailsWith<ErroPromocao> { f.servico.criar(CriarPromocaoRequest(1, 3)) }.codigo)
        assertEquals(400, assertFailsWith<ErroPromocao> { f.servico.criar(CriarPromocaoRequest(-1, 1)) }.codigo)
        assertTrue(f.servico.listar().isEmpty())
    }

    @Test
    fun `ativacao valida desconto datas e revalida estoque`() = runBlocking {
        val f = PromocaoFixture()
        val p = f.servico.criar(CriarPromocaoRequest(1, 1))
        for (desconto in listOf(-1, 0, 100, 101)) assertEquals(400, assertFailsWith<ErroPromocao> {
            f.servico.ativar(p.id, f.ativacao().copy(descontoPct = desconto))
        }.codigo)
        for (dados in listOf(
            f.ativacao().copy(dataInicio = "2026-02-30"),
            f.ativacao().copy(dataInicio = f.hoje.minusDays(1).toString()),
            f.ativacao().copy(dataFim = f.hoje.minusDays(1).toString()),
            f.ativacao().copy(dataFim = f.hoje.plusDays(8).toString())
        )) assertEquals(400, assertFailsWith<ErroPromocao> { f.servico.ativar(p.id, dados) }.codigo)
        assertEquals(StatusPromocao.SUGERIDA, f.servico.listar().single().status)
        transaction(f.banco) { LotesTable.update({ LotesTable.id eq 1L }) { it[quantidadeG] = 0 } }
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.ativar(p.id, f.ativacao()) }.codigo)
    }

    @Test
    fun `dois pratos concorrentes disputam uma unica promocao por lote`() = runBlocking {
        val f = PromocaoFixture()
        val resultados = (1L..2L).map { prato -> async(Dispatchers.Default) {
            try { f.servico.criar(CriarPromocaoRequest(1, prato)); 200 }
            catch (erro: ErroPromocao) { erro.codigo }
        } }.awaitAll()
        assertEquals(listOf(200, 409), resultados.sorted())
        assertEquals(1, f.servico.listar().size)
    }

    @Test
    fun `confirmacoes concorrentes nao somam o mesmo valor duas vezes`() = runBlocking {
        val f = PromocaoFixture()
        val p = f.servico.criar(CriarPromocaoRequest(1, 1))
        f.servico.ativar(p.id, f.ativacao())
        f.hoje = f.hoje.plusDays(1)
        val resultados = (1..2).map { async(Dispatchers.Default) {
            f.servico.confirmar(p.id, ConfirmarPromocaoRequest(true))
        } }.awaitAll()
        assertEquals(resultados[0], resultados[1])
        assertEquals("15.18", f.servico.resumo().prejuizoEvitado)
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.criar(CriarPromocaoRequest(1, 2)) }.codigo)
    }

    @Test
    fun `legado fica preservado fora do ciclo sem lote inventado`() = runBlocking {
        val f = PromocaoFixture()
        val id = transaction(f.banco) { PromocoesTable.insert {
            it[produtoId] = 1L; it[descontoPct] = 10; it[motivo] = "Legado"
            it[status] = "ATIVA"; it[dataSugestao] = f.hoje.toKotlinLocalDate()
        }[PromocoesTable.id] }
        assertTrue(f.servico.listar().isEmpty())
        assertEquals("0.00", f.servico.resumo().prejuizoEvitado)
        assertEquals(409, assertFailsWith<ErroPromocao> { f.servico.ativar(id, f.ativacao()) }.codigo)
        transaction(f.banco) { assertEquals("ATIVA", PromocoesTable.selectAll().single()[PromocoesTable.status]) }
    }
}
