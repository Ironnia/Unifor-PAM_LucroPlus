package br.com.lucroplus.services

import br.com.lucroplus.database.*
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.sql.DriverManager

class PromocaoFixture(var hoje: LocalDate = LocalDate.now()) {
    val servico = CicloPromocaoService(1) { hoje }
    val banco = criarBanco()

    private fun criarBanco(): Database {
        val nome = "promo_" + UUID.randomUUID().toString().replace("-", "")
        if (System.getProperty("lucroplus.test.mysql") == "true") {

            val url = "jdbc:mysql://127.0.0.1:13316/"
            DriverManager.getConnection(url, "root", "").use { conexao ->
                conexao.createStatement().use { it.executeUpdate("CREATE DATABASE $nome") }
            }
            return Database.connect(url + nome, "com.mysql.cj.jdbc.Driver", "root", "")
        }
        return Database.connect("jdbc:h2:mem:$nome;MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
    }

    init {
        TransactionManager.defaultDatabase = banco
        transaction(banco) {
            SchemaUtils.create(br.com.lucroplus.database.RestaurantesTable, br.com.lucroplus.database.UsuariosTable, IngredientesTable, ProdutosTable, LotesTable, FichasTecnicasTable,
                AlertasTable, AcoesAlertaTable, PromocoesTable, VendasTable, ItensVendaTable)
            br.com.lucroplus.routes.prepararContaTeste()
            IngredientesTable.insert {
                it[IngredientesTable.restauranteId] = 1L
                it[id] = 1; it[nome] = "Tomate"; it[unidade] = "g"
            }
            for (prato in 1L..3L) {
                ProdutosTable.insert {
                it[ProdutosTable.restauranteId] = 1L
                    it[id] = prato; it[nome] = "Prato $prato"; it[preco] = BigDecimal("20.00")
                    it[categoria] = "Teste"; it[ativo] = true
                }
                if (prato != 3L) FichasTecnicasTable.insert {
                    it[produtoId] = prato; it[ingredienteId] = 1L
                    it[quantidadeUsada] = BigDecimal("100"); it[unidade] = "g"
                }
            }
        }
        lote(1, 7); lote(2, 7, salvo = false); lote(3, -1); lote(4, 15); lote(5, 7, quantidade = 0)
    }

    fun lote(idLote: Long, dias: Long, salvo: Boolean = true, quantidade: Int = 1234) = transaction(banco) {
        LotesTable.insert {
                it[LotesTable.restauranteId] = 1L
            it[id] = idLote; it[ingredienteId] = 1L; it[quantidadeG] = quantidade
            it[custoUnitario] = BigDecimal("0.0123"); it[dataEntrada] = hoje.toKotlinLocalDate()
            it[numeroLote] = if (idLote == 1L) "4021" else "${idLote}"
            it[dataValidade] = hoje.plusDays(dias + 1).toKotlinLocalDate()
        }
        if (salvo) {
            AlertasTable.insert {
                it[id] = idLote; it[loteId] = idLote; it[tipo] = "VENCIMENTO"
                it[mensagem] = "Teste"; it[dataAlerta] = hoje.toKotlinLocalDate(); it[visualizado] = true
            }
            AcoesAlertaTable.insert {
                it[loteId] = idLote; it[alertaId] = idLote
                it[acao] = "SALVAR_LOTE"; it[dataAcao] = hoje.toKotlinLocalDate()
            }
        }
    }
}
