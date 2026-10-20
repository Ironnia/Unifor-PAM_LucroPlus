package br.com.lucroplus.services

import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.FichasTecnicasTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.models.ProdutoDto
import br.com.lucroplus.models.RentabilidadeDto
import org.jetbrains.exposed.sql.Avg
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode

class ProdutoService(private val restauranteId: Long) {

    suspend fun listarProdutos(): List<ProdutoDto> = dbQuery {
        ProdutosTable
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .map {
                ProdutoDto(
                    id = it[ProdutosTable.id],
                    nome = it[ProdutosTable.nome],
                    descricao = it[ProdutosTable.descricao],
                    preco = it[ProdutosTable.preco].toDouble(),
                    categoria = it[ProdutosTable.categoria],
                    ativo = it[ProdutosTable.ativo]
                )
            }
    }

    suspend fun obterRentabilidadeProdutos(): List<RentabilidadeDto> = dbQuery {
        // Custo médio ponderado de cada ingrediente pelos lotes ativos
        val avgColumn = Avg(LotesTable.custoUnitario, 4)
        val custoMedioPorIngrediente = LotesTable
            .select(LotesTable.ingredienteId, avgColumn)
            .where { LotesTable.restauranteId eq restauranteId }
            .groupBy(LotesTable.ingredienteId)
            .associate {
                val ingId = it[LotesTable.ingredienteId]
                val avgCost = it[avgColumn]?.toDouble() ?: 0.0
                ingId to avgCost
            }

        val fichasPorProduto = (FichasTecnicasTable innerJoin ProdutosTable)
            .selectAll().where { ProdutosTable.restauranteId eq restauranteId }
            .groupBy({ it[FichasTecnicasTable.produtoId] }) {
                it[FichasTecnicasTable.ingredienteId] to it[FichasTecnicasTable.quantidadeUsada].toDouble()
            }

        val produtos = ProdutosTable
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .map { row ->
                val produtoId = row[ProdutosTable.id]
                val produtoNome = row[ProdutosTable.nome]
                val precoVenda = row[ProdutosTable.preco].toDouble()

                // CMV: Soma do custo dos ingredientes ponderados pela ficha técnica
                val ingredientesDaReceita = fichasPorProduto[produtoId] ?: emptyList()
                var custoProducao = 0.0
                for ((ingredienteId, quantidadeUsada) in ingredientesDaReceita) {
                    val custoIngrediente = custoMedioPorIngrediente[ingredienteId] ?: 0.0
                    custoProducao += (quantidadeUsada * custoIngrediente)
                }

                custoProducao = custoProducao.roundTo(2)
                val lucroBruto = (precoVenda - custoProducao).roundTo(2)
                val margemLucroPct = if (precoVenda > 0) {
                    ((lucroBruto / precoVenda) * 100.0).roundTo(2)
                } else {
                    0.0
                }

                RentabilidadeDto(
                    produtoId = produtoId,
                    produtoNome = produtoNome,
                    precoVenda = precoVenda,
                    custoProducao = custoProducao,
                    lucroBruto = lucroBruto,
                    margemLucroPct = margemLucroPct
                )
            }

        produtos.sortedByDescending { it.margemLucroPct }
    }

    private fun Double.roundTo(decimals: Int): Double {
        return BigDecimal(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }
}
