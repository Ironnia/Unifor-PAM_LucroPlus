package br.com.lucroplus.routes

import br.com.lucroplus.database.*
import br.com.lucroplus.models.*
import br.com.lucroplus.security.JwtConfig
import br.com.lucroplus.services.*
import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class IsolamentoRestauranteTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val mysql = System.getenv("LUCROPLUS_E1B_MYSQL") == "true"
    private val url = "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    private lateinit var banco: Database
    private lateinit var nomeBanco: String
    private val hoje get() = LocalDate.now()

    @BeforeTest fun preparar() {
        nomeBanco = "isolamento_test_" + UUID.randomUUID().toString().replace("-", "")
        banco = if (mysql) {
            DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
                it.createStatement().use { st -> st.executeUpdate("CREATE DATABASE $nomeBanco") }
            }
            Database.connect(url.replace("/?", "/$nomeBanco?"), "com.mysql.cj.jdbc.Driver", System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        } else Database.connect("jdbc:h2:mem:$nomeBanco;MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(RestaurantesTable, UsuariosTable, ProdutosTable, IngredientesTable, LotesTable,
                FichasTecnicasTable, VendasTable, ItensVendaTable, AlertasTable, AcoesAlertaTable, PromocoesTable,
                ConfiguracoesTable, ConfiguracoesRestauranteTable, MovimentosEstoqueTable)
            for (loja in 1L..2L) {
                RestaurantesTable.insert { it[id] = loja; it[nome] = "Loja $loja" }
                UsuariosTable.insert {
                    it[id] = loja; it[nome] = "Gerente $loja"; it[email] = "gerente$loja@example.invalid"
                    it[senhaHash] = "ficticio"; it[tipo] = "GERENTE"; it[restauranteId] = loja
                }
                ProdutosTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[idExterno] = "100"
                    it[nome] = "Prato comum"; it[preco] = BigDecimal(10 * loja); it[categoria] = "Teste"
                }
                IngredientesTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[idExterno] = "100"; it[nome] = "Tomate"; it[unidade] = "g"
                }
                LotesTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[idExterno] = "100"; it[ingredienteId] = loja
                    it[quantidadeG] = (1000 * loja).toInt(); it[custoUnitario] = BigDecimal("0.01")
                    it[dataEntrada] = hoje.toKotlinLocalDate(); it[dataValidade] = hoje.plusDays(7).toKotlinLocalDate()
                    it[numeroLote] = "LOJA-$loja"
                }
                FichasTecnicasTable.insert {
                    it[produtoId] = loja; it[ingredienteId] = loja; it[quantidadeUsada] = BigDecimal(100); it[unidade] = "g"
                }
                VendasTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[idExterno] = "100"
                    it[dataVenda] = hoje.minusDays(1).toKotlinLocalDate(); it[valorTotal] = BigDecimal(10 * loja)
                }
                ItensVendaTable.insert {
                    it[vendaId] = loja; it[produtoId] = loja; it[quantidade] = loja.toInt(); it[precoUnitario] = BigDecimal(10)
                }
            }
            UsuariosTable.insert {
                it[id] = 3; it[nome] = "Admin"; it[email] = "admin@example.invalid"; it[senhaHash] = "ficticio"; it[tipo] = "ADMIN"
            }
            UsuariosTable.insert {
                it[id] = 4; it[nome] = "Legado"; it[email] = "legado@example.invalid"; it[senhaHash] = "ficticio"; it[tipo] = "GERENTE"
            }
            ProdutosTable.insert { it[id] = 99; it[nome] = "Produto legado"; it[preco] = BigDecimal.ONE; it[categoria] = "Legado" }
            ConfiguracoesTable.insert { it[chave] = "pdv_password"; it[valor] = "senha-global-ficticia" }
        }
    }

    @AfterTest fun limpar() {
        if (mysql) {
            check(nomeBanco.matches(Regex("isolamento_test_[a-f0-9]{32}")))
            DriverManager.getConnection(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD")).use {
                it.createStatement().use { st -> st.executeUpdate("DROP DATABASE $nomeBanco") }
            }
        }
    }

    private fun token(id: Long, perfil: String = "GERENTE", restauranteId: Long? = null) =
        JwtConfig.generateToken(id, "conta$id@example.invalid", perfil, "Conta $id", restauranteId)
    private fun HttpRequestBuilder.conta(
        id: Long,
        corpo: String? = null,
        restauranteId: Long? = null
    ) {
        bearerAuth(token(id, restauranteId = restauranteId))
        if (corpo != null) { contentType(ContentType.Application.Json); setBody(corpo) }
    }
    private fun Application.modulo() {
        install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
        install(Authentication) { jwt("auth-jwt") {
            verifier(JwtConfig.verifier)
            validate { JWTPrincipal(it.payload) }
        } }
        routing {
            authRoutes(); loteRoutes(); alertaRoutes(); promocaoRoutes(); produtoRoutes()
            dashboardRoutes(); relatorioRoutes(); configuracaoRoutes(); importacaoRoutes()
        }
    }

    @Test fun `login dos gerentes inclui loja no corpo no me e na claim JWT`() = testApplication {
        application { modulo() }
        for (loja in 1L..2L) {
            val login = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"gerente$loja@example.invalid","senha":"ficticio"}""")
            }
            assertEquals(HttpStatusCode.OK, login.status)
            val body = Json.parseToJsonElement(login.bodyAsText()).jsonObject
            val acesso = body.getValue("token").jsonPrimitive.content
            assertEquals(loja, body.getValue("restauranteId").jsonPrimitive.long)
            assertEquals("Loja $loja", body.getValue("restauranteNome").jsonPrimitive.content)
            assertEquals(loja, JWT.decode(acesso).getClaim("restaurante_id").asLong())

            val me = client.get("/auth/me") { bearerAuth(acesso) }
            assertEquals(HttpStatusCode.OK, me.status)
            val perfil = Json.parseToJsonElement(me.bodyAsText()).jsonObject
            assertEquals(loja, perfil.getValue("restauranteId").jsonPrimitive.long)
            assertEquals("Loja $loja", perfil.getValue("restauranteNome").jsonPrimitive.content)
        }

        val admin = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin@example.invalid","senha":"ficticio"}""")
        }
        val adminBody = Json.parseToJsonElement(admin.bodyAsText()).jsonObject
        assertEquals(JsonNull, adminBody["restauranteId"])
        assertNull(JWT.decode(adminBody.getValue("token").jsonPrimitive.content).getClaim("restaurante_id").asLong())
    }

    @Test fun `rotas antes publicas exigem sessao e legado nao ganha loja`() = testApplication {
        application { modulo() }
        val consultas = listOf("/produtos", "/produtos/rentabilidade", "/dashboard/kpis", "/dashboard/vendas",
            "/dashboard/previsao", "/relatorios/desperdicio", "/relatorios/historico", "/lotes", "/alertas/vencimento",
            "/promocoes", "/promocoes/pendentes", "/promocoes/sugestoes", "/promocoes/resumo")
        for (rota in consultas) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(rota).status, rota)
            assertEquals(HttpStatusCode.Forbidden, client.get("$rota?restauranteId=1") { conta(4) }.status, rota)
        }
        for (rota in listOf("produtos", "ingredientes", "ficha-tecnica", "vendas", "pdv")) {
            assertEquals(HttpStatusCode.Unauthorized, client.post("/importacao/$rota").status)
            assertEquals(HttpStatusCode.Forbidden, client.post("/importacao/$rota") { conta(4) }.status)
        }
        transaction(banco) { assertNull(UsuariosTable.selectAll().where { UsuariosTable.id eq 4L }.single()[UsuariosTable.restauranteId]) }
    }

    @Test fun `consultas legitimas usam a loja do banco e selecao cruzada retorna 403`() = testApplication {
        application { modulo() }
        for (loja in 1L..2L) {
            val outraLoja = 3 - loja
            for (rota in listOf("/lotes", "/produtos", "/alertas/vencimento", "/promocoes/sugestoes")) {
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client.get("$rota?restauranteId=$outraLoja") { conta(loja, restauranteId = loja) }.status,
                    rota
                )
            }

            val resposta = client.get("/lotes") { conta(loja, restauranteId = loja) }
            assertEquals(HttpStatusCode.OK, resposta.status)
            assertEquals(listOf(loja), json.decodeFromString<List<LoteItemDto>>(resposta.bodyAsText()).map { it.id })
            val produtos = client.get("/produtos") { conta(loja, restauranteId = loja) }
            assertEquals(listOf(loja), json.decodeFromString<List<ProdutoDto>>(produtos.bodyAsText()).map { it.id })
            val rentabilidade = json.decodeFromString<List<RentabilidadeDto>>(client.get("/produtos/rentabilidade") { conta(loja) }.bodyAsText())
            assertEquals(listOf(loja), rentabilidade.map { it.produtoId })
            assertEquals(1.0, rentabilidade.single().custoProducao)
            val kpis = client.get("/dashboard/kpis") { conta(loja) }
            assertEquals(10.0 * loja, json.decodeFromString<DashboardKpisDto>(kpis.bodyAsText()).faturamentoTotal)
            val graficos = json.decodeFromString<DashboardVendasResponse>(client.get("/dashboard/vendas") { conta(loja) }.bodyAsText())
            assertEquals(listOf(loja), graficos.top5Produtos.map { it.id })
            val previsao = json.decodeFromString<List<PrevisaoDemandaDto>>(client.get("/dashboard/previsao") { conta(loja) }.bodyAsText())
            assertTrue(previsao.all { it.produtoId == loja })
        }
    }

    @Test fun `alerta cruzado nao muda banco e cada fila sugestao pertence a sua loja`() = testApplication {
        application { modulo() }
        val a = json.decodeFromString<List<AlertaDto>>(client.get("/alertas/vencimento") { conta(1) }.bodyAsText()).single()
        val b = json.decodeFromString<List<AlertaDto>>(client.get("/alertas/vencimento") { conta(2) }.bodyAsText()).single()
        for (acao in listOf("salvar-lote", "ciente", "visualizar")) {
            assertEquals(HttpStatusCode.NotFound, client.patch("/alertas/${b.id}/$acao") { conta(1) }.status)
        }
        transaction(banco) {
            assertFalse(AlertasTable.selectAll().where { AlertasTable.id eq b.id }.single()[AlertasTable.visualizado])
            assertEquals(0L, AcoesAlertaTable.selectAll().count())
        }
        assertEquals(HttpStatusCode.OK, client.patch("/alertas/${a.id}/salvar-lote") { conta(1) }.status)
        assertEquals(HttpStatusCode.OK, client.patch("/alertas/${b.id}/salvar-lote") { conta(2) }.status)
        for (loja in 1L..2L) {
            val fila = json.decodeFromString<List<LotePendentePromocaoDto>>(client.get("/promocoes/pendentes") { conta(loja) }.bodyAsText())
            assertEquals(listOf(loja), fila.map { it.loteId })
            val sugestoes = json.decodeFromString<List<SugestaoPromocaoDto>>(client.get("/promocoes/sugestoes") { conta(loja) }.bodyAsText())
            assertEquals(listOf(loja), sugestoes.map { it.pratoId })
            assertTrue(sugestoes.all { it.loteId == loja && it.vendas30Dias == loja.toInt() })
        }
    }

    @Test fun `criar ativar recusar confirmar promocao cruzada nao escreve`() = testApplication {
        application { modulo() }
        val alerta = json.decodeFromString<List<AlertaDto>>(client.get("/alertas/vencimento") { conta(2) }.bodyAsText()).single()
        client.patch("/alertas/${alerta.id}/salvar-lote") { conta(2) }
        assertEquals(HttpStatusCode.NotFound, client.post("/promocoes") { conta(1, """{"loteId":2,"pratoId":2}""") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/promocoes") { conta(2, """{"loteId":2,"pratoId":1}""") }.status)
        val resposta = client.post("/promocoes") { conta(2, """{"loteId":2,"pratoId":2,"restauranteId":1}""") }
        assertEquals(HttpStatusCode.OK, resposta.status)
        val promocao = json.decodeFromString<PromocaoDto>(resposta.bodyAsText())
        for ((acao, corpo) in listOf("ativar" to """{"descontoPct":10,"dataInicio":"$hoje","dataFim":"$hoje"}""",
            "recusar" to "{}", "confirmar" to """{"loteEscoado":true}""")) {
            assertEquals(HttpStatusCode.NotFound, client.patch("/promocoes/${promocao.id}/$acao") { conta(1, corpo) }.status)
        }
        assertEquals("[]", client.get("/promocoes") { conta(1) }.bodyAsText().trim())
        transaction(banco) {
            val row = PromocoesTable.selectAll().single()
            assertEquals(2L, row[PromocoesTable.restauranteId]); assertEquals("SUGERIDA", row[PromocoesTable.status])
            assertNull(row[PromocoesTable.dataConfirmacao])
        }
        assertEquals(HttpStatusCode.OK, client.patch("/promocoes/${promocao.id}/ativar") {
            conta(2, """{"descontoPct":10,"dataInicio":"$hoje","dataFim":"$hoje"}""")
        }.status)
    }

    @Test fun `importacao de nomes iguais atualiza apenas loja autenticada e grava filhos corretos`() = testApplication {
        application { modulo() }
        for ((rota, csv) in listOf(
            "produtos" to "nome;descricao;preco;categoria\nPrato comum;Loja A;35;Teste",
            "ingredientes" to "nome;unidade;quantidade;custo;validade;lote;data_entrada;id_externo\nTomate;g;1000;0.01;${hoje.plusDays(5)};IMPORTADO-A;${hoje}T08:00:00-03:00;csv-a",
            "ficha-tecnica" to "produto;ingrediente;quantidade;unidade\nPrato comum;Tomate;200;g",
            "vendas" to "produto;data_venda;quantidade;id_externo\nPrato comum;${hoje}T10:00:00-03:00;3;csv-a")) {
            assertEquals(HttpStatusCode.Forbidden, client.post("/importacao/$rota?restauranteId=2") {
                conta(1, restauranteId = 1); contentType(ContentType.Text.CSV); setBody(csv)
            }.status)
            val resposta = client.post("/importacao/$rota") { conta(1, restauranteId = 1); contentType(ContentType.Text.CSV); setBody(csv) }
            assertEquals(HttpStatusCode.OK, resposta.status)
            assertTrue(json.decodeFromString<ImportacaoResponse>(resposta.bodyAsText()).sucesso)
        }
        transaction(banco) {
            assertEquals(BigDecimal("20.00"), ProdutosTable.selectAll().where { ProdutosTable.id eq 2L }.single()[ProdutosTable.preco])
            assertEquals(BigDecimal("100.0000"), FichasTecnicasTable.selectAll().where { FichasTecnicasTable.produtoId eq 2L }.single()[FichasTecnicasTable.quantidadeUsada])
            assertEquals(1L, LotesTable.selectAll().where { LotesTable.numeroLote eq "IMPORTADO-A" }.single()[LotesTable.restauranteId])
            assertEquals(1L, VendasTable.selectAll().where { VendasTable.origem eq "csv_import" }.single()[VendasTable.restauranteId])
        }
    }

    @Test fun `so ADMIN configura testa lista lojas e resposta nunca expoe senha`() = testApplication {
        application { modulo() }
        val config = """{"host":"simulado","usuario":"mock","senha":"senha-ficticia-A"}"""
        for (loja in 1L..2L) {
            assertEquals(HttpStatusCode.Forbidden, client.get("/configuracoes/restaurantes") { conta(loja) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.get("/configuracoes/pdv?restauranteId=1") { conta(loja) }.status)
            for (rota in listOf("/configuracoes/pdv", "/configuracoes/pdv/testar-conexao")) {
                assertEquals(HttpStatusCode.Forbidden, client.post("$rota?restauranteId=1") { conta(loja, config) }.status)
            }
        }
        assertEquals(HttpStatusCode.BadRequest, client.post("/configuracoes/pdv") { conta(3, config) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/configuracoes/pdv?restauranteId=999") { conta(3) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/configuracoes/pdv?restauranteId=1") { conta(3, config) }.status)
        val publica = client.get("/configuracoes/pdv?restauranteId=1") { conta(3) }.bodyAsText()
        assertFalse(publica.contains("senha-ficticia-A")); assertFalse(publica.contains("senha-global-ficticia"))
        assertEquals(HttpStatusCode.OK, client.post("/configuracoes/pdv?restauranteId=1") {
            conta(3, """{"host":"simulado","usuario":"mock"}""")
        }.status)
        runBlocking {
            assertEquals("senha-ficticia-A", ConfiguracaoService(1).obterPdvConfig(true).password)
            assertNull(ConfiguracaoService(2).obterPdvConfig(true).password)
        }
        assertEquals(HttpStatusCode.OK, client.post("/configuracoes/pdv/testar-conexao?restauranteId=1") { conta(3, config) }.status)
    }

    @Test fun `sincronizacao usa somente conexao salva da propria loja`() = testApplication {
        application { modulo() }
        client.post("/configuracoes/pdv?restauranteId=1") { conta(3, """{"host":"simulado","usuario":"mock","senha":"ficticia"}""") }
        val a = client.post("/importacao/pdv") { conta(1, restauranteId = 1) }
        assertFalse(json.decodeFromString<ImportacaoResponse>(a.bodyAsText()).sucesso)
        assertTrue(a.bodyAsText().contains("MySQL real"))
        val b = client.post("/importacao/pdv") { conta(2, restauranteId = 2) }
        assertFalse(json.decodeFromString<ImportacaoResponse>(b.bodyAsText()).sucesso)
        assertFalse(a.bodyAsText().contains("ficticia")); assertFalse(b.bodyAsText().contains("senha-global-ficticia"))
    }

    @Test fun `relatorios separados preservam perda por loja`() = testApplication {
        transaction(banco) {
            for (loja in 1L..2L) LotesTable.insert {
                it[restauranteId] = loja; it[ingredienteId] = loja; it[quantidadeG] = (100 * loja).toInt()
                it[custoUnitario] = BigDecimal("0.01"); it[dataEntrada] = hoje.minusDays(10).toKotlinLocalDate()
                it[dataValidade] = hoje.minusDays(1).toKotlinLocalDate()
            }
        }
        application { modulo() }
        for (loja in 1L..2L) {
            val historico = json.decodeFromString<List<DesperdicioHistoricoDto>>(client.get("/relatorios/historico") { conta(loja) }.bodyAsText())
            assertEquals(loja.toDouble(), historico.sumOf { it.valorPerdidoRs })
            val perdas = client.get("/relatorios/desperdicio") { conta(loja) }
            assertEquals(HttpStatusCode.OK, perdas.status)
            val rows = json.decodeFromString<List<DesperdicioIngredienteDto>>(perdas.bodyAsText())
            assertTrue(rows.all { it.ingrediente == "Tomate" }); if (rows.isNotEmpty()) assertEquals(loja.toDouble(), rows.sumOf { it.valorPerdidoRs })
        }
    }

    @Test fun `conta desativada ou removida perde acesso apesar de JWT valido e perfil forjado`() = testApplication {
        application { modulo() }
        assertEquals(HttpStatusCode.Forbidden, client.get("/configuracoes/restaurantes") { bearerAuth(token(1, "ADMIN")) }.status)
        transaction(banco) { UsuariosTable.update({ UsuariosTable.id eq 1L }) { it[ativo] = false } }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/produtos") { conta(1) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/produtos") { conta(999) }.status)
        transaction(banco) { UsuariosTable.update({ UsuariosTable.id eq 2L }) { it[restauranteId] = 1 } }
        val lotes = json.decodeFromString<List<LoteItemDto>>(client.get("/lotes") { conta(2) }.bodyAsText())
        assertEquals(listOf(1L), lotes.map { it.id })
    }

    @Test fun `mesmo id externo cabe nas duas lojas mas nao duplica dentro de uma loja`() {
        transaction(banco) {
            for (table in listOf(ProdutosTable, IngredientesTable, LotesTable, VendasTable)) assertEquals(2L, table.selectAll().where {
                when (table) {
                    ProdutosTable -> ProdutosTable.idExterno eq "100"
                    IngredientesTable -> IngredientesTable.idExterno eq "100"
                    LotesTable -> LotesTable.idExterno eq "100"
                    else -> VendasTable.idExterno eq "100"
                }
            }.count())
        }
        assertFails {
            transaction(banco) { ProdutosTable.insert {
                it[restauranteId] = 1; it[idExterno] = "100"; it[nome] = "Duplicado"; it[preco] = BigDecimal.ONE; it[categoria] = "Teste"
            } }
        }
    }

    @Test fun `cadastro publico nao permite criar ADMIN nem escolher loja`() = testApplication {
        application { modulo() }
        val resposta = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nome":"Nova","email":"nova@example.invalid","senha":"ficticia123","tipo":"ADMIN","restauranteId":2}""")
        }
        assertEquals(HttpStatusCode.Created, resposta.status)
        transaction(banco) {
            val conta = UsuariosTable.selectAll().where { UsuariosTable.email eq "nova@example.invalid" }.single()
            assertEquals("GERENTE", conta[UsuariosTable.tipo]); assertNull(conta[UsuariosTable.restauranteId])
        }
    }
}
