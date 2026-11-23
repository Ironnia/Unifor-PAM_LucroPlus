package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.models.DashboardKpisDto
import br.com.lucroplus.models.StatusPromocao
import br.com.lucroplus.routes.dashboardRoutes
import br.com.lucroplus.security.JwtConfig
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
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class DashboardServiceTest {
    private lateinit var banco: Database
    private val json = Json { ignoreUnknownKeys = true }
    private val relogio = Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), DashboardService.FUSO_FORTALEZA)

    @BeforeTest
    fun prepararBanco() {
        banco = Database.connect(
            "jdbc:h2:mem:dashboard_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(
                RestaurantesTable, UsuariosTable, ProdutosTable, IngredientesTable,
                LotesTable, VendasTable, ItensVendaTable, PromocoesTable, MovimentosEstoqueTable
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
                    it[id] = loja; it[restauranteId] = loja; it[nome] = "Ingrediente $loja"; it[unidade] = "g"
                }
                LotesTable.insert {
                    it[id] = loja; it[restauranteId] = loja; it[ingredienteId] = loja
                    it[quantidadeG] = 1000; it[custoUnitario] = BigDecimal("0.01")
                    it[dataEntrada] = LocalDate.of(2026, 9, 1).toKotlinLocalDate()
                    it[dataValidade] = LocalDate.of(2026, 10, 20).toKotlinLocalDate()
                }
            }
        }
    }

    @Test
    fun `monta MTD parcial e omite comparativo quando o dia nao existe no mes anterior`() {
        val parcial = CalculoDashboard.periodosMtd(LocalDate.of(2026, 4, 15))
        assertEquals(LocalDate.of(2026, 4, 1), parcial.atual.inicio)
        assertEquals(LocalDate.of(2026, 4, 15), parcial.atual.fim)
        assertEquals(LocalDate.of(2026, 3, 1), parcial.anterior?.inicio)
        assertEquals(LocalDate.of(2026, 3, 15), parcial.anterior?.fim)

        val virada = CalculoDashboard.periodosMtd(LocalDate.of(2026, 3, 31))
        assertNull(virada.anterior)
    }

    @Test
    fun `calcula MTD impacto serie de sete dias e isolamento por loja`() = kotlinx.coroutines.runBlocking {
        transaction(banco) {
            venda(1, 1, "2026-10-01", "100.00", 2)
            venda(2, 1, "2026-10-04", "50.00", 1)
            venda(7, 1, "2026-10-04", "777.00", 1, "2026-10-04T18:00:00-03:00")
            venda(3, 1, "2026-10-05", "999.00", 1)
            venda(4, 1, "2026-09-01", "50.00", 1)
            venda(5, 1, "2026-09-04", "50.00", 1)
            venda(6, 2, "2026-10-02", "900.00", 1)

            val lotesExtras = listOf(11L, 12L, 13L, 14L)
            for (idLote in lotesExtras) LotesTable.insert {
                it[id] = idLote; it[restauranteId] = 1; it[ingredienteId] = 1
                it[quantidadeG] = 100; it[custoUnitario] = BigDecimal("0.05")
                it[dataEntrada] = LocalDate.of(2026, 9, 1).toKotlinLocalDate()
                it[dataValidade] = LocalDate.of(2026, 10, 1).toKotlinLocalDate()
            }
            promocao(1, 11, StatusPromocao.CONCLUIDA_SUCESSO, "42.00", null, "2026-10-02T12:00:00-03:00")
            promocao(2, 12, StatusPromocao.EXPIRADA_COM_PERDA, "0.00", "18.00", "2026-10-03T12:00:00-03:00")
            promocao(3, 13, StatusPromocao.CONCLUIDA_SUCESSO, "10.00", null, "2026-09-02T12:00:00-03:00")
            MovimentosEstoqueTable.insert {
                it[restauranteId] = 1; it[loteId] = 14; it[ingredienteId] = 1; it[tipo] = "DESCARTE"
                it[quantidadeG] = 100; it[ocorridoEm] = InstantesEstoque.parse("2026-10-03T13:00:00-03:00")
            }
            MovimentosEstoqueTable.insert {
                it[restauranteId] = 1; it[loteId] = 12; it[ingredienteId] = 1; it[tipo] = "DESCARTE"
                it[quantidadeG] = 100; it[ocorridoEm] = InstantesEstoque.parse("2026-10-03T13:00:00-03:00")
                it[promocaoId] = 2
            }
        }

        val loja1 = DashboardService(1, relogio).obterKpis()
        assertEquals(150.0, loja1.faturamentoTotal)
        assertEquals(2, loja1.totalPedidos)
        assertEquals(3, loja1.totalProdutosVendidos)
        assertEquals(75.0, loja1.ticketMedio)
        assertEquals(50.0, loja1.variacoes.faturamentoPct)
        assertEquals(0.0, loja1.variacoes.totalPedidosPct)
        assertEquals(50.0, loja1.variacoes.ticketMedioPct)
        assertEquals(42.0, loja1.impactoFinanceiro.valorSalvo)
        assertEquals(23.0, loja1.impactoFinanceiro.valorPerdido)
        assertEquals(19.0, loja1.impactoFinanceiro.impactoLiquido)
        assertEquals(64.62, loja1.impactoFinanceiro.economiaGeradaPct)
        assertEquals(90.0, loja1.variacoes.impactoLiquidoPct)
        assertEquals(7, loja1.ultimosSeteDias.size)
        assertEquals("2026-09-28", loja1.ultimosSeteDias.first().data)
        assertEquals("2026-10-04", loja1.ultimosSeteDias.last().data)
        assertEquals(50.0, loja1.ultimosSeteDias.last().totalVendas)
        assertEquals(1, loja1.ultimosSeteDias.last().quantidadePedidos)

        val loja2 = DashboardService(2, relogio).obterKpis()
        assertEquals(900.0, loja2.faturamentoTotal)
        assertEquals(1, loja2.totalPedidos)
        assertEquals(0.0, loja2.impactoFinanceiro.valorSalvo)
        assertEquals(0.0, loja2.impactoFinanceiro.valorPerdido)
    }

    @Test
    fun `GET dashboard kpis exige JWT e responde payload completo`() = testApplication {
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JwtConfig.verifier)
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { dashboardRoutes() }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/dashboard/kpis").status)
        val resposta = client.get("/dashboard/kpis") {
            bearerAuth(JwtConfig.generateToken(1, "loja1@example.invalid", "GERENTE", "Gerente 1", 1))
        }
        assertEquals(HttpStatusCode.OK, resposta.status)
        val corpo = json.decodeFromString<DashboardKpisDto>(resposta.bodyAsText())
        assertEquals(7, corpo.ultimosSeteDias.size)
        assertNotNull(corpo.impactoFinanceiro)
        assertNotNull(corpo.periodoAtual)
    }

    private fun Transaction.venda(
        idVenda: Long,
        loja: Long,
        data: String,
        valor: String,
        quantidade: Int,
        instante: String? = null
    ) {
        VendasTable.insert {
            it[id] = idVenda; it[restauranteId] = loja
            it[dataVenda] = LocalDate.parse(data).toKotlinLocalDate(); it[valorTotal] = BigDecimal(valor)
            it[dataVendaEm] = instante?.let(InstantesEstoque::parse)
        }
        ItensVendaTable.insert {
            it[id] = idVenda; it[vendaId] = idVenda; it[produtoId] = loja
            it[ItensVendaTable.quantidade] = quantidade; it[precoUnitario] = BigDecimal.TEN
        }
    }

    private fun Transaction.promocao(
        idPromocao: Long,
        idLote: Long,
        estado: StatusPromocao,
        salvo: String,
        perdido: String?,
        confirmada: String
    ) {
        PromocoesTable.insert {
            it[id] = idPromocao; it[restauranteId] = 1; it[produtoId] = 1; it[loteId] = idLote
            it[motivo] = "Teste analítico"; it[status] = estado.name
            it[dataSugestao] = LocalDate.of(2026, 9, 1).toKotlinLocalDate()
            it[valorSalvo] = BigDecimal(salvo); it[valorPerdido] = perdido?.let(::BigDecimal)
            it[confirmadaEm] = InstantesEstoque.parse(confirmada)
        }
    }
}
