package br.com.lucroplus.routes

import br.com.lucroplus.database.BcgHistoricoTable
import br.com.lucroplus.database.FichasTecnicasTable
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.ItensVendaTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.database.RestaurantesTable
import br.com.lucroplus.database.UsuariosTable
import br.com.lucroplus.database.VendasTable
import br.com.lucroplus.models.BcgResponse
import br.com.lucroplus.services.BcgService
import br.com.lucroplus.services.CalculoBcg
import br.com.lucroplus.services.ProdutoBcg
import br.com.lucroplus.services.RankingService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BcgServiceTest {
    private val hoje = LocalDate.of(2026, 10, 3)
    private lateinit var banco: Database

    @BeforeTest
    fun prepararBanco() {
        banco = Database.connect(
            "jdbc:h2:mem:bcg_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(
                RestaurantesTable, UsuariosTable, ProdutosTable, IngredientesTable, LotesTable,
                FichasTecnicasTable, VendasTable, ItensVendaTable, BcgHistoricoTable
            )
            for (loja in 1L..2L) {
                RestaurantesTable.insert { it[id] = loja; it[nome] = "Loja $loja" }
                UsuariosTable.insert {
                    it[id] = loja
                    it[nome] = "Gerente $loja"
                    it[email] = "bcg$loja@example.invalid"
                    it[senhaHash] = "ficticio"
                    it[tipo] = "GERENTE"
                    it[restauranteId] = loja
                }
                IngredientesTable.insert {
                    it[id] = loja
                    it[restauranteId] = loja
                    it[nome] = "Ingrediente $loja"
                    it[unidade] = "g"
                }
                ProdutosTable.insert {
                    it[id] = loja
                    it[restauranteId] = loja
                    it[nome] = "Prato $loja"
                    it[preco] = BigDecimal("10.00")
                    it[categoria] = "Teste"
                }
                LotesTable.insert {
                    it[id] = loja
                    it[restauranteId] = loja
                    it[ingredienteId] = loja
                    it[quantidadeG] = 1000
                    it[custoUnitario] = BigDecimal("0.01")
                    it[dataEntrada] = hoje.minusMonths(1).toKotlinLocalDate()
                    it[dataValidade] = hoje.plusMonths(1).toKotlinLocalDate()
                }
                FichasTecnicasTable.insert {
                    it[produtoId] = loja
                    it[ingredienteId] = loja
                    it[quantidadeUsada] = BigDecimal("100")
                    it[unidade] = "g"
                }
                VendasTable.insert {
                    it[id] = loja
                    it[restauranteId] = loja
                    it[dataVenda] = hoje.minusDays(1).toKotlinLocalDate()
                    it[valorTotal] = BigDecimal("10.00")
                }
                ItensVendaTable.insert {
                    it[vendaId] = loja
                    it[produtoId] = loja
                    it[quantidade] = loja.toInt()
                    it[precoUnitario] = BigDecimal("10.00")
                }
            }
        }
    }

    @Test
    fun `classifica os quatro quadrantes comparando volume e margem com as medias`() {
        val produtos = listOf(
            ProdutoBcg(1, "Estrela", BigDecimal("10"), BigDecimal("2")),
            ProdutoBcg(2, "Burro", BigDecimal("10"), BigDecimal("8")),
            ProdutoBcg(3, "Quebra", BigDecimal("10"), BigDecimal("2")),
            ProdutoBcg(4, "Cao", BigDecimal("10"), BigDecimal("8"))
        )

        val resposta = CalculoBcg.calcular(produtos, mapOf(1L to 40, 2L to 40, 3L to 10, 4L to 10))

        assertEquals(25.0, resposta.mediaVolume)
        assertEquals(50.0, resposta.mediaMargemPct)
        assertEquals(
            listOf("ESTRELA", "BURRO_DE_CARGA", "QUEBRA_CABECA", "CAO"),
            resposta.pratos.map { it.categoriaBcg }
        )
    }

    @Test
    fun `empate exato nas duas medias pertence ao lado alto`() {
        val resposta = CalculoBcg.calcular(
            listOf(
                ProdutoBcg(1, "A", BigDecimal("10"), BigDecimal("5")),
                ProdutoBcg(2, "B", BigDecimal("10"), BigDecimal("5"))
            ),
            mapOf(1L to 7, 2L to 7)
        )

        assertEquals(listOf("ESTRELA", "ESTRELA"), resposta.pratos.map { it.categoriaBcg })
    }

    @Test
    fun `fechamento repetido nao duplica e preserva historico de outra loja`() = runBlocking {
        BcgService(1).fecharMes("2026-10", hoje)
        BcgService(1).fecharMes("2026-10", hoje)
        BcgService(2).fecharMes("2026-10", hoje)

        transaction(banco) {
            assertEquals(2L, BcgHistoricoTable.selectAll().count())
            assertEquals(
                setOf(1L, 2L),
                BcgHistoricoTable.selectAll().map { it[BcgHistoricoTable.restauranteId] }.toSet()
            )
        }
    }

    @Test
    fun `ranking recebe a mesma categoria calculada pela bcg`() = runBlocking {
        val ranking = RankingService(1).listar(hoje)
        val bcg = BcgService(1).listar(hoje)

        assertEquals(bcg.pratos.single().categoriaBcg, ranking.single().categoriaBcg)
        assertEquals("2026-10", ranking.single().desempenho6Meses.last().mes)
        assertEquals(9.0, ranking.single().desempenho6Meses.last().lucroBrutoTotal)
    }

    @Test
    fun `bcg usa janela movel de 30 dias e devolve historico da propria loja`() = runBlocking {
        transaction(banco) {
            VendasTable.insert {
                it[id] = 10L; it[restauranteId] = 1L
                it[dataVenda] = LocalDate.of(2026, 9, 30).toKotlinLocalDate()
                it[valorTotal] = BigDecimal("1000.00")
            }
            ItensVendaTable.insert {
                it[vendaId] = 10L; it[produtoId] = 1L
                it[quantidade] = 100; it[precoUnitario] = BigDecimal("10.00")
            }
            for ((idVenda, data, quantidade) in listOf(
                Triple(11L, hoje.minusDays(30), 7),
                Triple(12L, hoje.minusDays(31), 1000),
                Triple(13L, hoje.plusDays(1), 10000)
            )) {
                VendasTable.insert {
                    it[id] = idVenda; it[restauranteId] = 1L
                    it[dataVenda] = data.toKotlinLocalDate()
                    it[valorTotal] = BigDecimal(quantidade).multiply(BigDecimal.TEN)
                }
                ItensVendaTable.insert {
                    it[vendaId] = idVenda; it[produtoId] = 1L
                    it[ItensVendaTable.quantidade] = quantidade; it[precoUnitario] = BigDecimal("10.00")
                }
            }
            BcgHistoricoTable.insert {
                it[restauranteId] = 1L; it[produtoId] = 1L
                it[mesReferencia] = "2026-09"; it[categoria] = "CAO"
            }
            BcgHistoricoTable.insert {
                it[restauranteId] = 2L; it[produtoId] = 2L
                it[mesReferencia] = "2026-09"; it[categoria] = "ESTRELA"
            }
        }
        val prato = BcgService(1).listar(hoje).pratos.single()
        assertEquals(108, prato.vendas30Dias)
        assertEquals(listOf("2026-09"), prato.historicoBcg.map { it.mesReferencia })
        assertEquals(listOf("CAO"), prato.historicoBcg.map { it.categoria })
        val ranking = RankingService(1).listar(hoje).single()
        assertEquals(1107, ranking.desempenho6Meses.single { it.mes == "2026-09" }.vendasUnidades)
        assertEquals(9963.0, ranking.desempenho6Meses.single { it.mes == "2026-09" }.lucroBrutoTotal)
        assertEquals(listOf("2026-09"), ranking.historicoBcg.map { it.mesReferencia })
    }

    @Test
    fun `rotas exigem jwt e impedem escolher outra loja`() = testApplication {
        val algoritmo = Algorithm.HMAC256("segredo-ficticio-exclusivo-do-teste-bcg")
        fun token(id: Long) = JWT.create().withClaim("id", id).sign(algoritmo)

        application { moduloBcgTeste(algoritmo) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/bcg").status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/bcg?restauranteId=2") { bearerAuth(token(1)) }.status
        )
        val resposta = client.get("/bcg") { bearerAuth(token(1)) }
        assertEquals(HttpStatusCode.OK, resposta.status)
        assertEquals(listOf(1L), Json.decodeFromString<BcgResponse>(resposta.bodyAsText()).pratos.map { it.produtoId })

        val fechamento = client.post("/bcg/fechar-mes") {
            bearerAuth(token(1))
            contentType(ContentType.Application.Json)
            setBody("""{"mesReferencia":"2026-10"}""")
        }
        assertEquals(HttpStatusCode.OK, fechamento.status)
        transaction(banco) {
            assertEquals(listOf(1L), BcgHistoricoTable.selectAll().map { it[BcgHistoricoTable.restauranteId] })
        }
    }

    private fun Application.moduloBcgTeste(algoritmo: Algorithm) {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(algoritmo).build())
                validate { JWTPrincipal(it.payload) }
            }
        }
        routing { bcgRoutes() }
    }
}
