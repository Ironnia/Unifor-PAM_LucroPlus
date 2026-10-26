package br.com.lucroplus.services

import br.com.lucroplus.database.BcgHistoricoTable
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.FichasTecnicasTable
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.ItensVendaTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.database.VendasTable
import br.com.lucroplus.models.BcgFechamentoResponse
import br.com.lucroplus.models.BcgHistoricoItem
import br.com.lucroplus.models.BcgPratoDto
import br.com.lucroplus.models.BcgResponse
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

interface BcgOperacoes {
    suspend fun listar(fimJanela: LocalDate = LocalDate.now(ZoneId.of("America/Fortaleza"))): BcgResponse
    suspend fun fecharMes(
        mesReferencia: String,
        hoje: LocalDate = LocalDate.now(ZoneId.of("America/Fortaleza"))
    ): BcgFechamentoResponse
}

class BcgService(private val restauranteId: Long) : BcgOperacoes {
    override suspend fun listar(fimJanela: LocalDate): BcgResponse = dbQuery {
        val produtos = ProdutosTable.selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .orderBy(ProdutosTable.id, SortOrder.ASC)
            .toList()
        if (produtos.isEmpty()) return@dbQuery CalculoBcg.calcular(emptyList(), emptyMap())

        val ingredientes = IngredientesTable.selectAll()
            .where { IngredientesTable.restauranteId eq restauranteId }
            .associateBy { it[IngredientesTable.id] }
        val custoPorIngrediente = LotesTable.selectAll()
            .where { (LotesTable.restauranteId eq restauranteId) and (LotesTable.quantidadeG greater 0) }
            .groupBy { it[LotesTable.ingredienteId] }
            .mapValues { (_, lotes) ->
                CalculoRanking.custoPonderado(lotes.map {
                    it[LotesTable.quantidadeG] to it[LotesTable.custoUnitario]
                })
            }
        val fichas = FichasTecnicasTable.selectAll()
            .where { FichasTecnicasTable.produtoId inList produtos.map { it[ProdutosTable.id] } }
            .groupBy { it[FichasTecnicasTable.produtoId] }

        val entradas = produtos.mapNotNull { produto ->
            val itens = fichas[produto[ProdutosTable.id]].orEmpty()
            if (itens.isEmpty()) return@mapNotNull null
            val custos = itens.map { item ->
                val ingredienteId = item[FichasTecnicasTable.ingredienteId]
                val ingrediente = ingredientes[ingredienteId] ?: return@mapNotNull null
                val custoGrama = custoPorIngrediente[ingredienteId] ?: return@mapNotNull null
                val quantidadeG = CalculoRanking.converterParaGramas(
                    item[FichasTecnicasTable.quantidadeUsada],
                    item[FichasTecnicasTable.unidade],
                    ingrediente[IngredientesTable.pesoPorUnidadeG]
                ) ?: return@mapNotNull null
                quantidadeG.multiply(custoGrama)
            }
            ProdutoBcg(
                produto[ProdutosTable.id],
                produto[ProdutosTable.nome],
                produto[ProdutosTable.preco],
                custos.sumOf { it }
            )
        }
        if (entradas.isEmpty()) return@dbQuery CalculoBcg.calcular(emptyList(), emptyMap())

        val inicioJanela = fimJanela.minusDays(30)
        val vendas = (ItensVendaTable innerJoin VendasTable).selectAll().where {
            (VendasTable.restauranteId eq restauranteId) and
                (VendasTable.dataVenda greaterEq inicioJanela.toKotlinLocalDate()) and
                (VendasTable.dataVenda lessEq fimJanela.toKotlinLocalDate()) and
                (ItensVendaTable.produtoId inList entradas.map { it.produtoId })
        }.groupBy { it[ItensVendaTable.produtoId] }
            .mapValues { (_, itens) -> itens.sumOf { it[ItensVendaTable.quantidade] } }

        val foto = CalculoBcg.calcular(entradas, vendas)
        val primeiroMes = YearMonth.from(fimJanela).minusMonths(6).toString()
        val mesAtual = YearMonth.from(fimJanela).toString()
        val historico = BcgHistoricoTable.selectAll().where {
            (BcgHistoricoTable.restauranteId eq restauranteId) and
                (BcgHistoricoTable.mesReferencia greaterEq primeiroMes) and
                (BcgHistoricoTable.mesReferencia less mesAtual)
        }.groupBy { it[BcgHistoricoTable.produtoId] }
        foto.copy(pratos = foto.pratos.map { prato ->
            prato.copy(historicoBcg = historico[prato.produtoId].orEmpty()
                .sortedBy { it[BcgHistoricoTable.mesReferencia] }
                .map { BcgHistoricoItem(it[BcgHistoricoTable.mesReferencia], it[BcgHistoricoTable.categoria]) })
        })
    }

    override suspend fun fecharMes(mesReferencia: String, hoje: LocalDate): BcgFechamentoResponse {
        val referencia = runCatching { YearMonth.parse(mesReferencia) }
            .getOrElse { throw IllegalArgumentException("Mês de referência inválido. Use o formato YYYY-MM.") }
        require(!referencia.isAfter(YearMonth.from(hoje))) {
            "O mês de referência não pode estar no futuro."
        }
        val fimJanela = if (referencia == YearMonth.from(hoje)) hoje else referencia.atEndOfMonth()
        val foto = listar(fimJanela)

        dbQuery {
            BcgHistoricoTable.deleteWhere {
                (BcgHistoricoTable.restauranteId eq this@BcgService.restauranteId) and
                    (BcgHistoricoTable.mesReferencia eq mesReferencia)
            }
            BcgHistoricoTable.batchInsert(foto.pratos) { prato ->
                this[BcgHistoricoTable.restauranteId] = this@BcgService.restauranteId
                this[BcgHistoricoTable.produtoId] = prato.produtoId
                this[BcgHistoricoTable.mesReferencia] = mesReferencia
                this[BcgHistoricoTable.categoria] = prato.categoriaBcg
            }
        }
        return BcgFechamentoResponse(mesReferencia, foto.totalPratos, atualizado = true)
    }
}

internal data class ProdutoBcg(
    val produtoId: Long,
    val nome: String,
    val preco: BigDecimal,
    val cmv: BigDecimal
)

internal object CalculoBcg {
    const val ESTRELA = "ESTRELA"
    const val BURRO_DE_CARGA = "BURRO_DE_CARGA"
    const val QUEBRA_CABECA = "QUEBRA_CABECA"
    const val CAO = "CAO"

    fun calcular(produtos: List<ProdutoBcg>, vendas: Map<Long, Int>): BcgResponse {
        if (produtos.isEmpty()) return BcgResponse(0.0, 0.0, 0, emptyList())

        val calculados = produtos.map { produto ->
            val volume = vendas[produto.produtoId]?.coerceAtLeast(0) ?: 0
            val margemUnitaria = produto.preco.subtract(produto.cmv)
            val margemPct = if (produto.preco > BigDecimal.ZERO) {
                margemUnitaria.multiply(BigDecimal(100))
                    .divide(produto.preco, 8, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            PratoCalculado(produto, volume, margemPct, margemUnitaria.multiply(BigDecimal(volume)))
        }
        val divisor = BigDecimal(calculados.size)
        val mediaVolume = BigDecimal(calculados.sumOf { it.volume }).divide(divisor, 8, RoundingMode.HALF_UP)
        val mediaMargem = calculados.sumOf { it.margemPct }.divide(divisor, 8, RoundingMode.HALF_UP)

        val pratos = calculados.map { item ->
            val volumeAlto = BigDecimal(item.volume) >= mediaVolume
            val margemAlta = item.margemPct >= mediaMargem
            val categoria = when {
                item.volume == 0 -> CAO
                volumeAlto && margemAlta -> ESTRELA
                volumeAlto -> BURRO_DE_CARGA
                margemAlta -> QUEBRA_CABECA
                else -> CAO
            }
            BcgPratoDto(
                produtoId = item.produto.produtoId,
                nome = item.produto.nome,
                preco = item.produto.preco.moeda().toDouble(),
                cmv = item.produto.cmv.moeda().toDouble(),
                margemPct = item.margemPct.setScale(2, RoundingMode.HALF_UP).toDouble(),
                vendas30Dias = item.volume,
                lucro30Dias = item.lucro.setScale(2, RoundingMode.HALF_UP).toDouble(),
                categoriaBcg = categoria
            )
        }
        return BcgResponse(
            mediaVolume = mediaVolume.setScale(2, RoundingMode.HALF_UP).toDouble(),
            mediaMargemPct = mediaMargem.setScale(2, RoundingMode.HALF_UP).toDouble(),
            totalPratos = pratos.size,
            pratos = pratos
        )
    }

    private fun BigDecimal.moeda() = setScale(2, RoundingMode.HALF_UP)
}

private data class PratoCalculado(
    val produto: ProdutoBcg,
    val volume: Int,
    val margemPct: BigDecimal,
    val lucro: BigDecimal
)
