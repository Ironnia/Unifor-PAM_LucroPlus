package br.com.lucroplus.services

import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.database.FichasTecnicasTable
import br.com.lucroplus.database.ItensVendaTable
import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.database.VendasTable
import br.com.lucroplus.models.CompraHistoricoDto
import br.com.lucroplus.models.HistoricoComprasIngredienteResponse
import br.com.lucroplus.models.LoteItemDto
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class LoteService(private val restauranteId: Long) {
    suspend fun historicoCompras(
        ingredienteId: Long,
        hoje: LocalDate = LocalDate.now(ZoneId.of("America/Fortaleza"))
    ): HistoricoComprasIngredienteResponse? = dbQuery {
        val ingrediente = IngredientesTable.selectAll().where {
            (IngredientesTable.restauranteId eq restauranteId) and (IngredientesTable.id eq ingredienteId)
        }.singleOrNull() ?: return@dbQuery null
        val unidade = ingrediente[IngredientesTable.unidade]
        val fator = ingrediente[IngredientesTable.pesoPorUnidadeG]
        val inicioCompras = hoje.withDayOfMonth(1).minusMonths(5)
        val compras = LotesTable.selectAll().where {
            (LotesTable.restauranteId eq restauranteId) and
                (LotesTable.ingredienteId eq ingredienteId) and
                (LotesTable.dataEntrada greaterEq inicioCompras.toKotlinLocalDate()) and
                (LotesTable.dataEntrada lessEq hoje.toKotlinLocalDate())
        }.orderBy(LotesTable.dataEntrada to SortOrder.ASC, LotesTable.id to SortOrder.ASC).map { lote ->
            val entrada = LocalDate.parse(lote[LotesTable.dataEntrada].toString())
            val quantidade = lote[LotesTable.quantidadeInicialG]?.toLong()
                ?: lote[LotesTable.quantidadeOrigem]?.multiply(
                    BigDecimal(lote[LotesTable.gramasPorUnidadeOrigem] ?: fator)
                )?.setScale(0, RoundingMode.HALF_UP)?.toLong()
                ?: lote[LotesTable.quantidadeG].toLong()
            val custoGrama = lote[LotesTable.custoUnitario]
            val divisor = if (unidade.equals("un", true)) fator else 1000
            val preco = custoGrama.multiply(BigDecimal(divisor))
            val quantidadeExibida = when (unidade.lowercase()) {
                "kg" -> String.format(Locale.US, "%.2f kg", quantidade / 1000.0)
                "un" -> if (quantidade % fator.toLong() == 0L) "${quantidade / fator} un"
                    else String.format(Locale.US, "%.2f un", quantidade.toDouble() / fator)
                else -> "$quantidade g"
            }
            val meses = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
            CompraHistoricoDto(
                loteId = lote[LotesTable.id],
                numeroLote = lote[LotesTable.numeroLote] ?: "LOT-${lote[LotesTable.id]}",
                dataEntrada = entrada.toString(),
                mesReferencia = "${meses[entrada.monthValue - 1]}/${entrada.year % 100}",
                quantidadeCompradaG = quantidade,
                quantidadeFormatada = quantidadeExibida,
                precoPorKg = preco.setScale(4, RoundingMode.HALF_UP).toDouble(),
                custoTotal = custoGrama.multiply(BigDecimal(quantidade)).setScale(2, RoundingMode.HALF_UP).toDouble(),
                emEstoque = lote[LotesTable.quantidadeG] > 0,
                quantidadeRestanteG = lote[LotesTable.quantidadeG].toLong()
            )
        }

        val inicioConsumo = hoje.withDayOfMonth(1).minusMonths(3)
        val fimConsumo = hoje.withDayOfMonth(1).minusDays(1)
        val receitas = (FichasTecnicasTable innerJoin ProdutosTable).selectAll().where {
            (ProdutosTable.restauranteId eq restauranteId) and
                (FichasTecnicasTable.ingredienteId eq ingredienteId)
        }.associateBy { it[FichasTecnicasTable.produtoId] }
        val consumoTotal = (ItensVendaTable innerJoin VendasTable).selectAll().where {
            (VendasTable.restauranteId eq restauranteId) and
                (VendasTable.dataVenda greaterEq inicioConsumo.toKotlinLocalDate()) and
                (VendasTable.dataVenda lessEq fimConsumo.toKotlinLocalDate())
        }.sumOf { item ->
            val receita = receitas[item[ItensVendaTable.produtoId]] ?: return@sumOf BigDecimal.ZERO
            val gramas = CalculoRanking.converterParaGramas(
                receita[FichasTecnicasTable.quantidadeUsada],
                receita[FichasTecnicasTable.unidade], fator
            ) ?: return@sumOf BigDecimal.ZERO
            gramas.multiply(BigDecimal(item[ItensVendaTable.quantidade]))
        }
        HistoricoComprasIngredienteResponse(
            ingredienteId = ingredienteId,
            ingredienteNome = ingrediente[IngredientesTable.nome],
            unidade = unidade,
            consumoMedioMensalG = consumoTotal.divide(BigDecimal(3), 2, RoundingMode.HALF_UP).toDouble(),
            compras = compras
        )
    }

    suspend fun listarLotes(): List<LoteItemDto> = dbQuery {
        val hojeJava = LocalDate.now()

        (LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where { (IngredientesTable.restauranteId eq restauranteId) and (LotesTable.restauranteId eq restauranteId) and (LotesTable.quantidadeG greater 0) }
            .orderBy(LotesTable.dataValidade, SortOrder.ASC)
            .map { row ->
                val id = row[LotesTable.id]
                val ingredienteId = row[LotesTable.ingredienteId]
                val ingredienteNome = row[IngredientesTable.nome]
                val unidade = row[IngredientesTable.unidade]
                val pesoPorUnidadeG = row[IngredientesTable.pesoPorUnidadeG]
                val quantidadeG = row[LotesTable.quantidadeG]
                val custo = row[LotesTable.custoUnitario]
                val custoUnitario = custo.toDouble()
                val dataValidadeKmp = row[LotesTable.dataValidade]
                val dataEntradaKmp = row[LotesTable.dataEntrada]
                val numeroLote = row[LotesTable.numeroLote] ?: "LOT-$id"
                val observacao = row[LotesTable.observacao]

                val validadeJava = LocalDate.parse(dataValidadeKmp.toString())
                val prazoLimiteVenda = RegraValidadeLote.prazoLimiteVenda(validadeJava)
                val diasRestantes = RegraValidadeLote.diasAtePrazo(validadeJava, hojeJava)
                val criticidade = RegraValidadeLote.criticidade(diasRestantes)

                val quantidadeFormatada = when (unidade.lowercase()) {
                    "kg" -> String.format(Locale.US, "%.2f kg", quantidadeG / 1000.0)
                    "un" -> if (pesoPorUnidadeG > 0) "${quantidadeG / pesoPorUnidadeG} un" else "$quantidadeG un"
                    "l" -> if (pesoPorUnidadeG > 0) String.format(Locale.US, "%.2f L", quantidadeG.toDouble() / pesoPorUnidadeG) else "$quantidadeG g"
                    else -> "$quantidadeG g"
                }

                LoteItemDto(
                    id = id,
                    ingredienteId = ingredienteId,
                    ingredienteNome = ingredienteNome,
                    unidade = unidade,
                    quantidadeG = quantidadeG,
                    quantidadeFormatada = quantidadeFormatada,
                    custoUnitario = custoUnitario,
                    dataValidade = dataValidadeKmp.toString(),
                    dataEntrada = dataEntradaKmp.toString(),
                    numeroLote = numeroLote,
                    observacao = observacao,
                    diasRestantes = diasRestantes,
                    prazoLimiteVenda = prazoLimiteVenda.toString(),
                    diasParaPrazoLimite = diasRestantes,
                    criticidade = criticidade,
                    quantidadeInicialG = row[LotesTable.quantidadeInicialG],
                    quantidadeRestanteG = quantidadeG,
                    custoPorGrama = custo.setScale(8).toPlainString(),
                    origem = row[LotesTable.origem],
                    idExterno = row[LotesTable.idExterno],
                    compraIdExterno = row[LotesTable.compraIdExterno],
                    entradaEm = row[LotesTable.entradaEm]?.let { InstantesEstoque.iso(it) }
                )
            }
    }
}
