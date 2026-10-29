package br.com.lucroplus.services

import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.models.PdvConfigDto
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

internal data class ProdutoPdv(val id: String, val tipo: String)
internal data class CompraPdv(val id: Long, val produto: String, val em: Instant,
    val quantidade: BigDecimal, val preco: BigDecimal, val unidade: String, val fator: Int?, val validade: LocalDate)
internal data class VendaPdv(val id: Long, val produto: String, val em: Instant,
    val quantidade: Int, val preco: BigDecimal, val total: BigDecimal)
internal data class EstoquePdv(val lote: String, val produto: String, val gramas: Int, val corte: Instant)
internal data class DadosPdv(val produtos: List<ProdutoPdv>, val compras: List<CompraPdv>,
    val vendas: List<VendaPdv>, val posicoes: List<EstoquePdv>)

internal object OrigemPdv {

    fun simularDemonstracao(loja: Long, hoje: LocalDate = LocalDate.now()): DadosPdv {
        val pratos = ProdutosTable.selectAll().where {
            (ProdutosTable.restauranteId eq loja) and (ProdutosTable.ativo eq true)
        }.orderBy(ProdutosTable.id, SortOrder.ASC)
            .filter { !it[ProdutosTable.idExterno].isNullOrBlank() }
            .map { it[ProdutosTable.idExterno]!! to it[ProdutosTable.preco] }
        require(pratos.isNotEmpty()) { "Nenhum prato ativo está mapeado para o PDV desta loja" }
        val vendas = listOf(hoje.minusDays(1) to 16, hoje to 22).flatMap { (dia, total) ->
            (0 until total).map { indice ->
                val (produto, preco) = pratos[indice % pratos.size]
                val quantidade = 1 + indice % 3
                val horario = LocalDateTime.of(dia.year, dia.monthValue, dia.dayOfMonth,
                    9 + indice * 11 / total, indice * 37 % 60)
                VendaPdv(dia.format(DateTimeFormatter.BASIC_ISO_DATE).toLong() * 1000 + indice + 1,
                    produto, InstantesEstoque.parse(horario.toString()), quantidade,
                    preco, preco.multiply(BigDecimal(quantidade)))
            }
        }
        return DadosPdv(pratos.map { ProdutoPdv(it.first, "PRATO") }, emptyList(), vendas, emptyList())
    }

    fun ler(config: PdvConfigDto, loja: Long): DadosPdv {
        val url = requireNotNull(config.url) { "Configure o PDV desta loja" }
        require(url.startsWith("jdbc:mysql://") && !url.contains("simulado", true) &&
            !config.username.equals("mock", true)) { "Importação exige origem MySQL real no contrato FIFO" }
        val propriedades = Properties().apply {
            setProperty("user", config.username.orEmpty()); setProperty("password", config.password.orEmpty())
            setProperty("connectionTimeZone", "UTC"); setProperty("forceConnectionTimeZoneToSession", "true")
            setProperty("preserveInstants", "true"); setProperty("connectTimeout", "5000")
            setProperty("socketTimeout", "5000")
        }
        return DriverManager.getConnection(url, propriedades).use { conexao ->

            conexao.createStatement().use { it.execute("SET time_zone='+00:00'") }
            conexao.isReadOnly = true
            conexao.transactionIsolation = java.sql.Connection.TRANSACTION_REPEATABLE_READ
            conexao.autoCommit = false
            fun <T> linhas(sql: String, mapear: (ResultSet) -> T): List<T> =
                conexao.prepareStatement(sql).use { st ->
                    st.queryTimeout = 10; st.setLong(1, loja)
                    st.executeQuery().use { rs -> buildList { while (rs.next()) add(mapear(rs)) } }
                }
            fun ResultSet.instante(campo: String) = Instant.parse(requireNotNull(getTimestamp(campo,
                java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))).toInstant().toString())
            try {
                val produtos = linhas("SELECT id,tipo FROM pdv_produto WHERE restaurante_id=? ORDER BY id") {
                    ProdutoPdv(it.getLong("id").toString(), it.getString("tipo"))
                }
                val compras = linhas("SELECT * FROM pdv_compra WHERE restaurante_id=? ORDER BY compra_em,id") {
                    CompraPdv(it.getLong("id"), it.getLong("produto_id").toString(), it.instante("compra_em"),
                        it.getBigDecimal("quantidade"), it.getBigDecimal("preco_custo"), it.getString("unidade"),
                        it.getObject("gramas_por_unidade")?.let { n -> (n as Number).toInt() },
                        requireNotNull(it.getDate("data_validade")).toLocalDate())
                }
                val vendas = linhas("SELECT * FROM pdv_venda WHERE restaurante_id=? ORDER BY data_venda,id") {
                    VendaPdv(it.getLong("id"), it.getLong("produto_id").toString(), it.instante("data_venda"),
                        it.getBigDecimal("quantidade").intValueExact(), it.getBigDecimal("preco_unitario"), it.getBigDecimal("valor_total"))
                }
                val posicoes = linhas("SELECT * FROM pdv_estoque WHERE restaurante_id=? ORDER BY lote_id") {
                    EstoquePdv(it.getLong("lote_id").toString(), it.getLong("produto_id").toString(),
                        it.getInt("quantidade_atual_g"), it.instante("corte_em"))
                }
                conexao.commit()
                DadosPdv(produtos, compras, vendas, posicoes)
            } catch (e: Exception) { conexao.rollback(); throw e }
        }
    }
}
