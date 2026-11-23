package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.models.FaixaCobertura
import br.com.lucroplus.models.PrevisaoComprasResponse
import br.com.lucroplus.routes.estoquePrevisaoRoutes
import br.com.lucroplus.security.JwtConfig
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PrevisaoComprasServiceTest {
    private lateinit var banco: Database
    private val json = Json { ignoreUnknownKeys = true }
    private val relogio = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)

    @BeforeTest
    fun prepararBanco() {
        banco = Database.connect(
            "jdbc:h2:mem:previsao_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(
                RestaurantesTable, UsuariosTable, ProdutosTable, IngredientesTable,
                LotesTable, FichasTecnicasTable, VendasTable, ItensVendaTable
            )
            for (loja in 1L..2L) {
                RestaurantesTable.insert { it[id] = loja; it[nome] = "Loja $loja" }
                UsuariosTable.insert {
                    it[id] = loja; it[nome] = "Gerente $loja"; it[email] = "loja$loja@example.invalid"
                    it[senhaHash] = "ficticio"; it[tipo] = "GERENTE"; it[restauranteId] = loja
                }
                ProdutosTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[nome] = "Prato $loja"
                    it[preco] = BigDecimal.TEN; it[categoria] = "Teste"
                }
                IngredientesTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[nome] = "Ingrediente $loja"
                    it[unidade] = "kg"; it[pesoPorUnidadeG] = 1000
                }
                FichasTecnicasTable.insert {
                    it[produtoId] = loja; it[ingredienteId] = loja
                    it[quantidadeUsada] = BigDecimal("100"); it[unidade] = "g"
                }
            }

            IngredientesTable.insert {
                it[id] = 3; it[restauranteId] = 1; it[nome] = "Sem histórico"
                it[unidade] = "g"; it[pesoPorUnidadeG] = 1
            }
            lote(1, 1, 1, 500, "2026-11-30")
            lote(2, 2, 2, 9000, "2026-11-30")
            venda(1, 1, 1, "2026-07-15", 10)
            venda(2, 1, 1, "2026-08-15", 10)
            venda(3, 1, 1, "2026-09-15", 10)
            venda(4, 2, 2, "2026-07-15", 90)
        }
    }

    @Test
    fun `calcula demanda projecao sugestao e isolamento por restaurante`() = kotlinx.coroutines.runBlocking {
        val resposta = PrevisaoComprasService(1, relogio).obter()
        assertEquals("2026-07-01", resposta.periodoHistoricoInicio)
        assertEquals("2026-09-30", resposta.periodoHistoricoFim)
        assertEquals("2026-11", resposta.proximoMes)
        assertEquals(2, resposta.itens.size)

        val calculado = resposta.itens.first { it.ingredienteId == 1L }
        assertEquals(1000.0, calculado.demandaMediaMensalG)
        assertEquals(0.0, calculado.estoqueProjetadoInicioMesG)
        assertEquals(50.0, calculado.coberturaPct)
        assertEquals(FaixaCobertura.ATENCAO, calculado.faixa)
        assertEquals(0.5, calculado.quantidadeSugeridaCompra)
        assertEquals("kg", calculado.unidadeExibicao)

        val insuficiente = resposta.itens.first { it.ingredienteId == 3L }
        assertEquals(FaixaCobertura.DADOS_INSUFICIENTES, insuficiente.faixa)
        assertEquals(1, resposta.resumo.atencao)
        assertEquals(1, resposta.resumo.dadosInsuficientes)

        val outraLoja = PrevisaoComprasService(2, relogio).obter()
        assertEquals(listOf(2L), outraLoja.itens.map { it.ingredienteId })
        assertEquals(FaixaCobertura.SEGURO, outraLoja.itens.single().faixa)
    }

    @Test
    fun `respeita limites exatos de cinquenta e cem por cento`() {
        val cinquenta = CalculoPrevisaoCompras.item(
            1, "Atenção", "g", 1, 500, BigDecimal("500"), BigDecimal("1000")
        )
        val cem = CalculoPrevisaoCompras.item(
            2, "Seguro", "g", 1, 1000, BigDecimal("1000"), BigDecimal("1000")
        )
        val abaixo = CalculoPrevisaoCompras.item(
            3, "Urgente", "g", 1, 499, BigDecimal("499.99"), BigDecimal("1000")
        )
        assertEquals(FaixaCobertura.ATENCAO, cinquenta.faixa)
        assertEquals(500.0, cinquenta.quantidadeSugeridaG)
        assertEquals(FaixaCobertura.SEGURO, cem.faixa)
        assertEquals(0.0, cem.quantidadeSugeridaG)
        assertEquals(FaixaCobertura.URGENTE, abaixo.faixa)
    }

    @Test
    fun `GET exige JWT e usa a loja da conta autenticada`() = testApplication {
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JwtConfig.verifier)
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { estoquePrevisaoRoutes() }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/estoque/previsao-compras").status)
        val resposta = client.get("/estoque/previsao-compras") {
            bearerAuth(JwtConfig.generateToken(1, "loja1@example.invalid", "GERENTE", "Gerente 1", 1))
        }
        assertEquals(HttpStatusCode.OK, resposta.status)
        val corpo = json.decodeFromString<PrevisaoComprasResponse>(resposta.bodyAsText())
        assertEquals(setOf(1L, 3L), corpo.itens.map { it.ingredienteId }.toSet())
    }

    private fun Transaction.lote(idLote: Long, loja: Long, ingrediente: Long, saldo: Int, validade: String) {
        LotesTable.insert {
            it[id] = idLote; it[restauranteId] = loja; it[ingredienteId] = ingrediente
            it[quantidadeG] = saldo; it[custoUnitario] = BigDecimal("0.01")
            it[dataEntrada] = LocalDate.of(2026, 7, 1).toKotlinLocalDate()
            it[dataValidade] = LocalDate.parse(validade).toKotlinLocalDate()
        }
    }

    private fun Transaction.venda(
        idVenda: Long,
        loja: Long,
        produto: Long,
        data: String,
        quantidade: Int
    ) {
        VendasTable.insert {
            it[id] = idVenda; it[restauranteId] = loja
            it[dataVenda] = LocalDate.parse(data).toKotlinLocalDate(); it[valorTotal] = BigDecimal.TEN
        }
        ItensVendaTable.insert {
            it[id] = idVenda; it[vendaId] = idVenda; it[produtoId] = produto
            it[ItensVendaTable.quantidade] = quantidade; it[precoUnitario] = BigDecimal.TEN
        }
    }
}
