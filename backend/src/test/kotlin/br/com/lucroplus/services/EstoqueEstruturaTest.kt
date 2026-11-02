package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class EstoqueEstruturaTest {
    private val mysql = System.getenv("LUCROPLUS_ESTOQUE_MYSQL") == "true"
    private val url = "jdbc:mysql://127.0.0.1:${System.getenv("LUCROPLUS_TEST_MYSQL_PORT") ?: "3306"}/?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true"
    private val nomeBanco = "estoque_test_" + UUID.randomUUID().toString().replace("-", "")
    private lateinit var banco: Database
    private val hoje = LocalDate.of(2026, 10, 1)
    private val cabecalho = "nome;unidade;quantidade;custo_unitario;data_validade;numero_lote;peso_por_unidade_g;id_externo;compra_id_externo;origem;data_entrada\n"
    private fun compra(unidade: String = "kg", quantidade: String = "10", custo: String = "6", peso: String = "",
                       id: String = "lote-1", origem: String = "pdv", nome: String = "Tomate") =
        cabecalho + "$nome;$unidade;$quantidade;$custo;2026-10-09;L-1;$peso;$id;compra-1;$origem;2026-10-01T08:00:00-03:00"

    @BeforeTest fun preparar() {
        banco = if (mysql) {
            DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
                it.createStatement().use { st -> st.executeUpdate("CREATE DATABASE $nomeBanco") }
            }
            Database.connect(url.replace("/?", "/$nomeBanco?"), "com.mysql.cj.jdbc.Driver",
                System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        } else Database.connect("jdbc:h2:mem:$nomeBanco;MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(RestaurantesTable, UsuariosTable, IngredientesTable, ProdutosTable, LotesTable,
                FichasTecnicasTable, VendasTable, ItensVendaTable, PromocoesTable, AlertasTable,
                AcoesAlertaTable, MovimentosEstoqueTable)
            for (loja in 1L..2L) {
                RestaurantesTable.insert { it[id] = loja; it[nome] = "Loja $loja" }
                ProdutosTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[nome] = "Prato"
                    it[preco] = BigDecimal("20"); it[categoria] = "Teste"
                }
            }
        }
    }

    @AfterTest fun limpar() {
        if (mysql) {
            require(nomeBanco.matches(Regex("estoque_test_[a-f0-9]{32}")))
            DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
                it.createStatement().use { st -> st.executeUpdate("DROP DATABASE $nomeBanco") }
            }
        }
    }

    private fun receitaTomate(loja: Long = 1) = transaction(banco) {
        val ingrediente = IngredientesTable.selectAll().where {
            (IngredientesTable.restauranteId eq loja) and (IngredientesTable.nome eq "Tomate")
        }.single()[IngredientesTable.id]
        if (!FichasTecnicasTable.selectAll().where { FichasTecnicasTable.produtoId eq loja }.any()) {
            FichasTecnicasTable.insert {
                it[produtoId] = loja; it[ingredienteId] = ingrediente
                it[quantidadeUsada] = BigDecimal("200"); it[unidade] = "g"
            }
        }
    }

    @Test fun `10 kg a seis reais por kg preserva 10000 g e custo de sessenta reais`() = runBlocking {
        assertTrue(ImportacaoService(1).importarIngredientes(compra()).sucesso)
        val lote = LoteService(1).listarLotes().single()
        assertEquals(10000, lote.quantidadeInicialG); assertEquals(10000, lote.quantidadeRestanteG)
        assertEquals(lote.quantidadeG, lote.quantidadeRestanteG)
        assertEquals("0.00600000", lote.custoPorGrama)
        assertEquals(BigDecimal("60.00"), BigDecimal(lote.custoPorGrama).multiply(BigDecimal(10000)).setScale(2))
        assertEquals("2026-10-09", lote.dataValidade); assertEquals("2026-10-08", lote.prazoLimiteVenda)
        assertEquals("2026-10-01T08:00-03:00", lote.entradaEm)
        transaction(banco) {
            val row = LotesTable.selectAll().single()
            assertEquals("pdv", row[LotesTable.origem]); assertEquals("compra-1", row[LotesTable.compraIdExterno])
            assertEquals(1000, row[LotesTable.gramasPorUnidadeOrigem])
        }
    }

    @Test fun `unidade e litro usam equivalencias explicitas inclusive no custo da ficha`() = runBlocking {
        val service = ImportacaoService(1)
        assertTrue(service.importarIngredientes(compra("un", "2", "3", "80", "pao", nome="Pão")).sucesso)
        assertTrue(service.importarIngredientes(compra("L", "2", "12", "900", "oleo", nome="Óleo")).sucesso)
        val lotes = LoteService(1).listarLotes().associateBy { it.ingredienteNome }
        assertEquals(160, lotes.getValue("Pão").quantidadeInicialG)
        assertEquals(1800, lotes.getValue("Óleo").quantidadeInicialG)
        assertEquals("2.00 L", lotes.getValue("Óleo").quantidadeFormatada)
        assertEquals("0.03750000", lotes.getValue("Pão").custoPorGrama)
        val custo = CalculoSugestaoPromocao.custoItem(BigDecimal("2"), "l", 900, BigDecimal("0.01333333"))!!
        assertEquals(BigDecimal("24.00"), custo.setScale(2, java.math.RoundingMode.HALF_UP))
    }

    @Test fun `un litro grama fracionaria e overflow invalidos nao deixam ingrediente ou lote`() = runBlocking {
        val service = ImportacaoService(1)
        for (csv in listOf(compra("un"), compra("l"), compra("g", "0.5"), compra("kg", "2147484"),
            compra("caixa"), compra(custo="-1"), compra("un", peso="0"))) {
            assertFalse(service.importarIngredientes(csv).sucesso)
        }
        transaction(banco) {
            assertEquals(0, LotesTable.selectAll().count().toInt())
            assertEquals(0, IngredientesTable.selectAll().count().toInt())
        }
    }

    @Test fun `reimportar compra nao repoe saldo e dados divergentes nao alteram original`() = runBlocking {
        val service = ImportacaoService(1)
        service.importarIngredientes(compra())
        transaction(banco) { LotesTable.update({ LotesTable.id greater 0 }) { it[quantidadeG] = 3000 } }
        assertTrue(service.importarIngredientes(compra()).sucesso)
        assertFalse(service.importarIngredientes(compra(quantidade="11")).sucesso)
        val lote = LoteService(1).listarLotes().single()
        assertEquals(10000, lote.quantidadeInicialG); assertEquals(3000, lote.quantidadeRestanteG)
    }

    @Test fun `origem e loja fazem parte da identidade de compras e vendas`() = runBlocking {

        for (loja in 1L..2L) {
            for (origem in listOf("pdv", "csv_import")) {
                assertTrue(ImportacaoService(loja).importarIngredientes(compra(origem=origem)).sucesso)
            }
            receitaTomate(loja)
        }
        for (loja in 1L..2L) for (origem in listOf("pdv", "csv_import")) {
            val csv = "produto;data_venda;quantidade;id_externo;origem\nPrato;2026-10-01T10:00:00-03:00;2;v1;$origem"
            assertTrue(ImportacaoService(loja).importarVendas(csv).sucesso)
            assertTrue(ImportacaoService(loja).importarVendas(csv).sucesso)
        }
        transaction(banco) {
            assertEquals(4, LotesTable.selectAll().count().toInt())
            assertEquals(4, VendasTable.selectAll().count().toInt())
            assertEquals(4, ItensVendaTable.selectAll().count().toInt())
        }
        assertEquals(2, LoteService(1).listarLotes().size)
    }

    @Test fun `venda conserva instante original e dia civil de Fortaleza na virada UTC`() = runBlocking {
        val service = ImportacaoService(1)
        service.importarIngredientes(compra()); receitaTomate()
        val csv = "produto;data_venda;quantidade;id_externo\nPrato;2026-10-02T02:30:00Z;2;v1"
        assertTrue(service.importarVendas(csv).sucesso)
        assertTrue(service.importarVendas(csv).sucesso)
        assertFalse(service.importarVendas(csv.replace(";2;", ";3;")).sucesso)
        transaction(banco) {
            val venda = VendasTable.selectAll().single()
            assertEquals("2026-10-01", venda[VendasTable.dataVenda].toString())
            assertEquals("2026-10-01T23:30-03:00", InstantesEstoque.iso(venda[VendasTable.dataVendaEm]!!))
            assertNotNull(venda[VendasTable.importadoEm])
            assertEquals(1, ItensVendaTable.selectAll().count().toInt())
        }
    }

    @Test fun `venda nova somente com dia nao ganha horario ficticio nem baixa incompleta`() = runBlocking {
        assertFalse(ImportacaoService(1).importarVendas("produto;data;quantidade\nPrato;2026-10-01;1").sucesso)
        transaction(banco) { assertEquals(0L, VendasTable.selectAll().count()) }
    }

    @Test fun `lote legado conserva saldo sem quantidade inicial ou origem inventadas`() = runBlocking {
        transaction(banco) {
            val ingrediente = IngredientesTable.insert {
                it[restauranteId] = 1; it[nome] = "Legado"; it[unidade] = "g"
            }[IngredientesTable.id]
            LotesTable.insert {
                it[restauranteId] = 1; it[ingredienteId] = ingrediente; it[quantidadeG] = 3000
                it[custoUnitario] = BigDecimal("0.006"); it[dataEntrada] = hoje.toKotlinLocalDate()
                it[dataValidade] = hoje.plusDays(8).toKotlinLocalDate()
            }
        }
        val lote = LoteService(1).listarLotes().single()
        assertNull(lote.quantidadeInicialG); assertNull(lote.origem); assertNull(lote.entradaEm)
        assertEquals(3000, lote.quantidadeRestanteG)
        transaction(banco) { assertEquals(0, MovimentosEstoqueTable.selectAll().count().toInt()) }
    }

    @Test fun `estrutura impede repetir consumo do mesmo item no mesmo lote`() = runBlocking {
        ImportacaoService(1).importarIngredientes(compra())

        transaction(banco) {
            val venda = VendasTable.insert {
                it[restauranteId]=1; it[dataVenda]=hoje.toKotlinLocalDate(); it[valorTotal]=BigDecimal("20")
            }[VendasTable.id]
            ItensVendaTable.insert {
                it[vendaId]=venda; it[produtoId]=1; it[quantidade]=1; it[precoUnitario]=BigDecimal("20")
            }
        }
        fun inserir() = transaction(banco) {
            val lote = LotesTable.selectAll().single()
            MovimentosEstoqueTable.insert {
                it[restauranteId] = 1; it[loteId] = lote[LotesTable.id]
                it[ingredienteId] = lote[LotesTable.ingredienteId]; it[tipo] = "CONSUMO_VENDA"
                it[quantidadeG] = 200; it[ocorridoEm] = InstantesEstoque.parse("2026-10-01T10:00:00-03:00")
                it[itemVendaId] = ItensVendaTable.selectAll().single()[ItensVendaTable.id]
            }
        }

        inserir()
        assertFailsWith<org.jetbrains.exposed.exceptions.ExposedSQLException> { inserir() }
        transaction(banco) {
            assertEquals(1, MovimentosEstoqueTable.selectAll().count().toInt())
            assertEquals(10000, LotesTable.selectAll().single()[LotesTable.quantidadeG])
        }
    }

    @Test fun `limites de prazo sao comuns em lotes alertas sugestoes e campanha`() = runBlocking {
        var dia = LocalDate.now()
        transaction(banco) {
            IngredientesTable.insert { it[id] = 1; it[restauranteId] = 1; it[nome] = "Tomate"; it[unidade] = "g" }
            FichasTecnicasTable.insert { it[produtoId] = 1; it[ingredienteId] = 1; it[quantidadeUsada] = BigDecimal("200"); it[unidade] = "g" }
            for ((idLote, prazo) in listOf(1L to 7L, 2L to 14L, 3L to 15L, 4L to -1L)) LotesTable.insert {
                it[id] = idLote; it[restauranteId] = 1; it[ingredienteId] = 1; it[quantidadeG] = 10000
                it[custoUnitario] = BigDecimal("0.006"); it[dataEntrada] = dia.minusDays(10).toKotlinLocalDate()
                it[dataValidade] = dia.plusDays(prazo+1).toKotlinLocalDate()
            }
        }
        val lotes = LoteService(1).listarLotes().associateBy { it.id }
        assertEquals(7, lotes.getValue(1).diasParaPrazoLimite)
        assertEquals(dia.plusDays(7).toString(), lotes.getValue(1).prazoLimiteVenda)
        assertEquals("ATENCAO", lotes.getValue(2).criticidade)
        assertEquals("SAUDAVEL", lotes.getValue(3).criticidade)
        val alertas = AlertaService(1).obterAlertasVencimento()
        assertEquals(setOf(1L, 2L), alertas.map { it.loteId }.toSet())
        assertEquals(lotes.getValue(1).prazoLimiteVenda, alertas.first { it.loteId == 1L }.prazoLimiteVenda)
        AlertaService(1).executarAcao(alertas.first { it.loteId == 1L }.id, AcaoAlerta.SALVAR_LOTE)
        val sugestao = MotorPromocaoService(1).listarSugestoes(dia).single()
        assertEquals(lotes.getValue(1).prazoLimiteVenda, sugestao.prazoLimiteVenda)
        assertEquals(7, sugestao.diasParaPrazoLimite)
        val servico = CicloPromocaoService(1) { dia }
        val p = servico.criar(CriarPromocaoRequest(1, 1))
        assertEquals(400, assertFailsWith<ErroPromocao> {
            servico.ativar(p.id, AtivarPromocaoRequest(10, dia.toString(), dia.plusDays(8).toString()))
        }.codigo)
        val ativa = servico.ativar(p.id, AtivarPromocaoRequest(10, dia.toString(), dia.plusDays(7).toString()))
        assertEquals(dia.plusDays(7).toString(), ativa.dataFim)
        assertNull(ativa.quantidadeConsumidaG); assertNull(ativa.valorPerdido)
        dia = dia.plusDays(7)
        assertFalse(servico.listar().single().aguardaConfirmacao)
        dia = dia.plusDays(1)
        assertTrue(servico.listar().single().aguardaConfirmacao)
    }
}
