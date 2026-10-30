package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.*
import kotlin.test.*

class ImportacaoPdvTest {
    @BeforeEach fun exigirMysql() { assumeTrue(System.getenv("LUCROPLUS_PDV_MYSQL") == "true", "FIFO requer dois MySQL reais isolados") }

    private fun conferir(cenario: String) {
        PdvFixture(cenario).use { f ->
            val origemAntes=f.hashOrigem()
            assertEquals(listOf(0L,0L,0L,0L), f.contagens()); assertNull(f.status())
            val respostas = (1L..2L).map { f.importar(it).also { r -> assertTrue(r.sucesso, r.erros.toString()) } }
            val esperado = Json.parseToJsonElement(File("../database/fifo_resultados_esperados.json").readText())
                .jsonObject.getValue("scenarios").jsonObject.getValue(cenario).jsonObject
            transaction(f.banco) {
                val lotes = LotesTable.selectAll().toList()
                val lotesPorId = lotes.associateBy { it[LotesTable.id] }
                val vendas = VendasTable.selectAll().associateBy { it[VendasTable.id] }
                val itens = ItensVendaTable.selectAll().associateBy { it[ItensVendaTable.id] }
                val saldos = lotes.associate {
                    (it[LotesTable.restauranteId]!!*100000 + it[LotesTable.idExterno]!!.toLong()).toString() to it[LotesTable.quantidadeG]
                }
                assertEquals(esperado.getValue("expected_balances").jsonObject.mapValues { it.value.jsonPrimitive.int }, saldos)
                val obtidos = MovimentosEstoqueTable.selectAll().map { m ->
                    val lote = lotesPorId.getValue(m[MovimentosEstoqueTable.loteId])
                    val venda = vendas.getValue(itens.getValue(m[MovimentosEstoqueTable.itemVendaId]!!)[ItensVendaTable.vendaId])
                    listOf((venda[VendasTable.restauranteId]!!*100000+venda[VendasTable.idExterno]!!.toLong()).toString(),
                        (lote[LotesTable.restauranteId]!!*100000+lote[LotesTable.idExterno]!!.toLong()).toString(),
                        m[MovimentosEstoqueTable.quantidadeG].toString(), m[MovimentosEstoqueTable.ocorridoEm].toString())
                }.sortedBy { it.joinToString("|") }
                val previstos = esperado.getValue("movements").jsonArray.map { e ->
                    val m = e.jsonObject
                    listOf(m.getValue("sale").jsonPrimitive.content, m.getValue("lot").jsonPrimitive.content,
                        m.getValue("grams").jsonPrimitive.content,
                        kotlinx.datetime.Instant.parse(m.getValue("at").jsonPrimitive.content.replace(' ', 'T')+"Z").toString())
                }.sortedBy { it.joinToString("|") }
                assertEquals(previstos, obtidos, "Movimentos $cenario")
                assertEquals(esperado.getValue("sales").jsonPrimitive.int, vendas.size)
                assertEquals(0L, PromocoesTable.selectAll().count())
                val tomate = lotes.first { it[LotesTable.restauranteId] == 1L && it[LotesTable.ingredienteId] == 101L }
                assertEquals(BigDecimal("0.00600000"), tomate[LotesTable.custoUnitario])
                val auditorias = SyncPdvHistoricoTable.selectAll().toList()
                assertEquals(2, auditorias.size)
                assertTrue(auditorias.all { it[SyncPdvHistoricoTable.status] == "SUCESSO" })
                assertEquals(respostas.sumOf { it.vendasNovas }, auditorias.sumOf { it[SyncPdvHistoricoTable.vendasImportadas] })
                assertEquals(respostas.sumOf { it.comprasNovas }, auditorias.sumOf { it[SyncPdvHistoricoTable.comprasImportadas] })
                assertEquals(respostas.sumOf { it.movimentosNovos }, auditorias.sumOf { it[SyncPdvHistoricoTable.movimentosGerados] })
                assertTrue(auditorias.all { it[SyncPdvHistoricoTable.tempoExecucaoMs] != null })
            }
            assertTrue(respostas.flatMap { it.conferenciaEstoque }.all { it.coincide })
            val estado = f.estado()
            for (loja in 1L..2L) {
                val r = f.importar(loja); assertTrue(r.sucesso)
                assertEquals(0, r.comprasNovas); assertEquals(0, r.vendasNovas); assertEquals(0, r.movimentosNovos)
                assertNotNull(r.ultimaSincronizacao); assertEquals(r.ultimaSincronizacao, f.status(loja))
            }
            assertEquals(estado, f.estado())
            transaction(f.banco) {
                val repeticoes = SyncPdvHistoricoTable.selectAll().where {
                    (SyncPdvHistoricoTable.status eq "SUCESSO") and
                        (SyncPdvHistoricoTable.vendasImportadas eq 0) and
                        (SyncPdvHistoricoTable.comprasImportadas eq 0) and
                        (SyncPdvHistoricoTable.movimentosGerados eq 0)
                }.count()
                assertEquals(2L, repeticoes)
            }
            assertEquals(origemAntes,f.hashOrigem(),"PDV apenas lido")
            println("PDV_MYSQL cenario=$cenario vendas=${respostas.sumOf { it.vendasNovas }} movimentos=${respostas.sumOf { it.movimentosNovos }} replay=sem_duplicacao")
        }
    }

    @Test fun `massa parcial`() = conferir("parcial")
    @Test fun `massa integral`() = conferir("integral")
    @Test fun `massa perda`() = conferir("perda")
    @Test fun `massa travessia`() = conferir("travessia")
    @Test fun `massa inicio efetivo`() = conferir("inicio_efetivo")
    @Test fun `massa inicio futuro`() = conferir("inicio_futuro")
    @Test fun `massa historico`() = conferir("historico")

    @Test fun `nova compra e nova venda entram uma vez com preco da origem`() {
        PdvFixture().use { f ->
            assertTrue(f.importar().sucesso)
            f.sql("INSERT INTO pdv_compra VALUES (1,99,1,'2026-10-01 11:00:00',1,6,'kg',1000,'2026-10-05')")
            f.sql("INSERT INTO pdv_venda VALUES (1,99,101,'2026-10-01 12:00:00',1,17,17)")
            val r = f.importar(); assertTrue(r.sucesso, r.erros.toString())
            assertEquals(1, r.comprasNovas); assertEquals(1, r.vendasNovas); assertEquals(4, r.movimentosNovos)
            assertEquals(2800, f.saldo()); assertEquals(1000, f.saldo(externo="99"))
            transaction(f.banco) {
                assertEquals(BigDecimal("17.00"), VendasTable.selectAll().where { VendasTable.idExterno eq "99" }.single()[VendasTable.valorTotal])
            }
            val estado = f.estado(); assertTrue(f.importar().sucesso); assertEquals(estado, f.estado())
        }
    }

    @Test fun `falta de ingrediente cancela compras vendas anteriores movimentos e horario do conjunto`() {
        PdvFixture().use { f ->
            assertTrue(f.importar().sucesso)
            val estado = f.estado(); val sucesso = f.status()
            f.sql("INSERT INTO pdv_compra VALUES (1,99,1,'2026-10-01 11:00:00',1,6,'kg',1000,'2026-10-05')")
            f.sql("INSERT INTO pdv_venda VALUES (1,99,101,'2026-10-01 12:00:00',1,20,20),(1,100,101,'2026-10-01 13:00:00',9999,20,199980)")
            val r = f.importar(); assertFalse(r.sucesso)
            assertEquals(estado, f.estado()); assertEquals(sucesso, f.status()); assertEquals(sucesso, r.ultimaSincronizacao)
            assertEquals(0, r.vendasNovas); assertEquals(0, r.movimentosNovos)
            transaction(f.banco) {
                val falha = SyncPdvHistoricoTable.selectAll().where { SyncPdvHistoricoTable.status eq "FALHA" }.single()
                assertEquals(0, falha[SyncPdvHistoricoTable.vendasImportadas])
                assertEquals(0, falha[SyncPdvHistoricoTable.comprasImportadas])
                assertEquals(0, falha[SyncPdvHistoricoTable.movimentosGerados])
                assertFalse(falha[SyncPdvHistoricoTable.mensagem].isNullOrBlank())
            }
        }
    }

    @Test fun `falha SQL no ultimo sucesso reverte ate os movimentos ja escritos`() {
        PdvFixture().use { f ->
            f.sql("CREATE TRIGGER pdv_falha BEFORE INSERT ON tb_configuracao_restaurante FOR EACH ROW BEGIN IF NEW.chave='pdv_ultimo_sucesso' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='falha ficticia final'; END IF; END", f.nome)
            assertFalse(f.importar().sucesso); assertEquals(listOf(0L,0L,0L,0L), f.contagens()); assertNull(f.status())
            transaction(f.banco) {
                assertEquals(1L, SyncPdvHistoricoTable.selectAll().where { SyncPdvHistoricoTable.status eq "FALHA" }.count())
            }
            f.sql("DROP TRIGGER pdv_falha", f.nome)
            assertTrue(f.importar().sucesso); assertEquals(3000, f.saldo())
            transaction(f.banco) {
                assertEquals(1L, SyncPdvHistoricoTable.selectAll().where { SyncPdvHistoricoTable.status eq "SUCESSO" }.count())
            }
        }
    }

    @Test fun `duas sincronizacoes concorrentes nao duplicam nem perdem saldo`() {
        PdvFixture().use { f ->
            val barreira = CyclicBarrier(2); val pool = Executors.newFixedThreadPool(2)
            try {
                val resultados = pool.invokeAll((1..2).map { Callable { barreira.await(10,TimeUnit.SECONDS); f.importar() } },60,TimeUnit.SECONDS).map { it.get() }
                assertTrue(resultados.all { it.sucesso }, resultados.flatMap { it.erros }.toString())
                assertEquals(2, resultados.sumOf { it.vendasNovas }); assertEquals(8, resultados.sumOf { it.movimentosNovos })
                assertEquals(3000, f.saldo()); assertEquals(listOf(4L,2L,2L,8L),f.contagens())
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun `posicao externa divergente nao altera saldo nem desfaz descarte local`() {
        PdvFixture().use { f ->
            assertTrue(f.importar().sucesso)
            f.sql("UPDATE pdv_estoque SET quantidade_atual_g=123 WHERE restaurante_id=1 AND lote_id=1")
            val r = f.importar(); assertTrue(r.sucesso); assertFalse(r.conferenciaEstoque.first().coincide)
            assertEquals(3000, f.saldo())
            transaction(f.banco) {
                val lote = LotesTable.selectAll().where { (LotesTable.restauranteId eq 1) and (LotesTable.idExterno eq "1") }.single()
                MovimentosEstoqueTable.insert {
                    it[restauranteId]=1; it[loteId]=lote[LotesTable.id]; it[ingredienteId]=lote[LotesTable.ingredienteId]
                    it[tipo]="DESCARTE"; it[quantidadeG]=3000; it[ocorridoEm]=InstantesEstoque.parse("2026-10-01T01:00:00-03:00")
                }
                LotesTable.update({ LotesTable.id eq lote[LotesTable.id] }) { it[quantidadeG]=0 }
            }
            val estado = f.estado(); assertTrue(f.importar().sucesso); assertEquals(0,f.saldo()); assertEquals(estado,f.estado())
        }
    }

    @Test fun `compra retroativa e venda retroativa novas sao recusadas sem mudar dados`() {
        PdvFixture().use { f ->
            assertTrue(f.importar().sucesso); val estado=f.estado(); val horario=f.status()
            f.sql("INSERT INTO pdv_compra VALUES (1,99,1,'2026-09-28 11:00:00',1,6,'kg',1000,'2026-10-05')")
            assertFalse(f.importar().sucesso); assertEquals(estado,f.estado()); assertEquals(horario,f.status())
            f.sql("DELETE FROM pdv_compra WHERE restaurante_id=1 AND id=99")
            f.sql("INSERT INTO pdv_venda VALUES (1,99,101,'2026-09-30 14:00:00',1,20,20)")
            assertFalse(f.importar().sucesso); assertEquals(estado,f.estado()); assertEquals(horario,f.status())
        }
    }

    @Test fun `alteracao de venda ou compra importada nao reescreve historico`() {
        PdvFixture().use { f ->
            assertTrue(f.importar().sucesso); val estado=f.estado(); val horario=f.status()
            f.sql("UPDATE pdv_venda SET preco_unitario=19,valor_total=380 WHERE restaurante_id=1 AND id=1")
            assertFalse(f.importar().sucesso); assertEquals(estado,f.estado()); assertEquals(horario,f.status())
            f.sql("UPDATE pdv_venda SET preco_unitario=20,valor_total=400 WHERE restaurante_id=1 AND id=1")
            f.sql("UPDATE pdv_compra SET quantidade=11 WHERE restaurante_id=1 AND id=1")
            assertFalse(f.importar().sucesso); assertEquals(estado,f.estado())
        }
    }

    @Test fun `compra de prato e venda de ingrediente sao recusadas`() {
        PdvFixture().use { f ->
            f.sql("INSERT INTO pdv_compra VALUES (1,99,101,'2026-10-01 11:00:00',1,6,'kg',1000,'2026-10-05')")
            assertFalse(f.importar().sucesso); assertEquals(listOf(0L,0L,0L,0L),f.contagens())
            f.sql("DELETE FROM pdv_compra WHERE restaurante_id=1 AND id=99")
            f.sql("INSERT INTO pdv_venda VALUES (1,99,1,'2026-10-01 12:00:00',1,20,20)")
            assertFalse(f.importar().sucesso); assertEquals(listOf(0L,0L,0L,0L),f.contagens())
        }
    }

    @Test fun `csv de vendas usa FIFO ordem temporal idempotencia e rollback do arquivo`() = runBlocking {
        PdvFixture().use { f ->
            assertTrue(f.importar().sucesso)
            val servico=ImportacaoService(1)
            val header="produto;data_venda;quantidade;id_externo\n"
            val csv=header+"Prato 1;2026-10-01T10:00:00-03:00;1;v2\nPrato 1;2026-10-01T09:00:00-03:00;1;v1"
            assertTrue(servico.importarVendas(csv).sucesso); assertEquals(2600,f.saldo())
            val estado=f.estado(); assertTrue(servico.importarVendas(csv).sucesso); assertEquals(estado,f.estado())
            val invalido=header+"Prato 1;2026-10-01T11:00:00-03:00;1;v3\nPrato 1;2026-10-01T12:00:00-03:00;9999;v4"
            assertFalse(servico.importarVendas(invalido).sucesso); assertEquals(estado,f.estado())
            assertFalse(servico.importarVendas(header+"Prato 1;2026-10-01;1;v3").sucesso)
            assertFalse(servico.importarVendas("produto;data_venda;quantidade\nPrato 1;2026-10-01T11:00:00-03:00;1").sucesso)
            assertEquals(estado,f.estado())
        }
    }

    @Test fun `conexao indisponivel nao vira sucesso nem expoe URL ou senha`() = runBlocking {
        PdvFixture().use { f ->
            ConfiguracaoService(1).salvarPdvConfig(br.com.lucroplus.models.PdvConfigDto(
                url="jdbc:mysql://127.0.0.1:1/origem_indisponivel", username="ficticio", password="senha-ficticia-pdv"))
            val r=f.importar(); assertFalse(r.sucesso); assertNull(r.ultimaSincronizacao)
            assertEquals(listOf(0L,0L,0L,0L),f.contagens()); assertNull(f.status())
            val body=Json.encodeToString(br.com.lucroplus.models.ImportacaoResponse.serializer(),r)
            assertFalse(body.contains("jdbc:")); assertFalse(body.contains("senha-ficticia-pdv"))
        }
    }

    @Test fun `equivalencia invalida total de venda incoerente e mapeamento ausente cancelam carga`() {
        PdvFixture().use { f ->
            f.sql("UPDATE pdv_compra SET gramas_por_unidade=0 WHERE restaurante_id=1 AND produto_id=3")
            assertFalse(f.importar().sucesso); assertEquals(listOf(0L,0L,0L,0L),f.contagens())
            f.sql("UPDATE pdv_compra SET gramas_por_unidade=80 WHERE restaurante_id=1 AND produto_id=3")
            f.sql("UPDATE pdv_venda SET valor_total=1 WHERE restaurante_id=1 AND id=1")
            assertFalse(f.importar().sucesso); assertEquals(listOf(0L,0L,0L,0L),f.contagens())
            f.sql("UPDATE pdv_venda SET valor_total=400 WHERE restaurante_id=1 AND id=1")
            transaction(f.banco) { IngredientesTable.update({ IngredientesTable.id eq 101 }) { it[idExterno]="sem-mapa" } }
            assertFalse(f.importar().sucesso); assertEquals(listOf(0L,0L,0L,0L),f.contagens()); assertNull(f.status())
        }
    }

    @Test fun `horario original e dia civil sobrevivem a URL JDBC com outro fuso`() = runBlocking {
        PdvFixture().use { f ->
            val config=ConfiguracaoService(1).obterPdvConfig(true)
            ConfiguracaoService(1).salvarPdvConfig(config.copy(url=config.url!!
                .replace("connectionTimeZone=UTC","connectionTimeZone=America/Los_Angeles")
                .replace("forceConnectionTimeZoneToSession=true","forceConnectionTimeZoneToSession=false")))
            f.sql("UPDATE pdv_venda SET data_venda='2026-10-02 02:30:00' WHERE restaurante_id=1 AND id=1")
            val r=f.importar(); assertTrue(r.sucesso,r.erros.toString()); assertEquals(3000,f.saldo())
            transaction(f.banco) {
                val venda=VendasTable.selectAll().where { (VendasTable.restauranteId eq 1) and (VendasTable.idExterno eq "1") }.single()
                assertEquals("2026-10-01",venda[VendasTable.dataVenda].toString())
                assertEquals("2026-10-01T23:30-03:00",InstantesEstoque.iso(venda[VendasTable.dataVendaEm]!!))
            }
        }
    }
}
