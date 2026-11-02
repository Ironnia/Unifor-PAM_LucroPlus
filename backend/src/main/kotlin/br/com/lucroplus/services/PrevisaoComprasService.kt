package br.com.lucroplus.services

import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.FichasTecnicasTable
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.ItensVendaTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.database.ProdutosTable
import br.com.lucroplus.database.VendasTable
import br.com.lucroplus.models.FaixaCobertura
import br.com.lucroplus.models.PrevisaoCompraItemDto
import br.com.lucroplus.models.PrevisaoComprasResponse
import br.com.lucroplus.models.ResumoPrevisaoComprasDto
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class PrevisaoComprasService(
    private val restauranteId: Long,
    private val relogio: Clock = Clock.system(FUSO_FORTALEZA)
) {
    companion object {
        val FUSO_FORTALEZA: ZoneId = ZoneId.of("America/Fortaleza")
    }

    suspend fun obter(): PrevisaoComprasResponse = dbQuery {
        val hoje = LocalDate.now(relogio)
        val inicioMesAtual = hoje.withDayOfMonth(1)
        val inicioHistorico = inicioMesAtual.minusMonths(3)
        val fimHistorico = inicioMesAtual.minusDays(1)
        val inicioProximoMes = inicioMesAtual.plusMonths(1)

        val ingredientes = IngredientesTable.selectAll()
            .where { IngredientesTable.restauranteId eq restauranteId }
            .associateBy { it[IngredientesTable.id] }
        val produtos = ProdutosTable.selectAll()
            .where { ProdutosTable.restauranteId eq restauranteId }
            .associateBy { it[ProdutosTable.id] }
        val fichas = FichasTecnicasTable.selectAll()
            .where { FichasTecnicasTable.produtoId inList produtos.keys.toList() }
            .groupBy { it[FichasTecnicasTable.produtoId] }

        val itensHistoricos = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where {
                (VendasTable.restauranteId eq restauranteId) and
                    (VendasTable.dataVenda greaterEq inicioHistorico.toKotlinLocalDate()) and
                    (VendasTable.dataVenda lessEq fimHistorico.toKotlinLocalDate())
            }
            .toList()

        val produtosVendidos = itensHistoricos.map { it[ItensVendaTable.produtoId] }.toSet()
        val produtosSemFicha = produtosVendidos.count { fichas[it].isNullOrEmpty() }
        val demandaTotalG = mutableMapOf<Long, BigDecimal>()
        val conversaoInvalida = mutableSetOf<Long>()

        itensHistoricos.forEach { item ->
            val quantidadeVendida = BigDecimal(item[ItensVendaTable.quantidade])
            fichas[item[ItensVendaTable.produtoId]].orEmpty().forEach { ficha ->
                val ingredienteId = ficha[FichasTecnicasTable.ingredienteId]
                val ingrediente = ingredientes[ingredienteId] ?: return@forEach
                val quantidadeReceitaG = runCatching {
                    BigDecimal(
                        ConversaoEstoque.quantidadeG(
                            ficha[FichasTecnicasTable.quantidadeUsada],
                            ficha[FichasTecnicasTable.unidade],
                            ingrediente[IngredientesTable.pesoPorUnidadeG]
                        )
                    )
                }.getOrElse {
                    conversaoInvalida += ingredienteId
                    return@forEach
                }
                demandaTotalG.merge(ingredienteId, quantidadeReceitaG * quantidadeVendida, BigDecimal::add)
            }
        }

        val lotes = LotesTable.selectAll()
            .where { (LotesTable.restauranteId eq restauranteId) and (LotesTable.quantidadeG greater 0) }
            .toList()
        val estoqueAtual = lotes
            .filter { LocalDate.parse(it[LotesTable.dataValidade].toString()) >= hoje }
            .groupBy { it[LotesTable.ingredienteId] }
            .mapValues { (_, linhas) -> linhas.sumOf { it[LotesTable.quantidadeG] } }
        val estoqueValidoNoProximoMes = lotes
            .filter { LocalDate.parse(it[LotesTable.dataValidade].toString()) >= inicioProximoMes }
            .groupBy { it[LotesTable.ingredienteId] }
            .mapValues { (_, linhas) -> linhas.sumOf { it[LotesTable.quantidadeG] } }

        val diasHistorico = ChronoUnit.DAYS.between(inicioHistorico, inicioMesAtual)
        val diasAteProximoMes = ChronoUnit.DAYS.between(hoje, inicioProximoMes)
        val itens = ingredientes.values.map { ingrediente ->
            val ingredienteId = ingrediente[IngredientesTable.id]
            val unidade = ingrediente[IngredientesTable.unidade]
            val pesoPorUnidadeG = ingrediente[IngredientesTable.pesoPorUnidadeG]
            val totalG = demandaTotalG[ingredienteId]
            val motivoInsuficiente = when {
                ingredienteId in conversaoInvalida -> "Conversão da ficha técnica não cadastrada"
                totalG == null || totalG.signum() == 0 -> "Sem vendas suficientes ou ficha técnica no período"
                runCatching { ConversaoEstoque.gramasPorUnidade(unidade, pesoPorUnidadeG) }.isFailure ->
                    "Conversão da unidade de compra não cadastrada"
                else -> null
            }
            if (motivoInsuficiente != null) {
                PrevisaoCompraItemDto(
                    ingredienteId = ingredienteId,
                    ingredienteNome = ingrediente[IngredientesTable.nome],
                    unidadeCompra = unidade,
                    estoqueAtualG = estoqueAtual[ingredienteId] ?: 0,
                    estoqueProjetadoInicioMesG = null,
                    demandaMediaMensalG = null,
                    coberturaPct = null,
                    faixa = FaixaCobertura.DADOS_INSUFICIENTES,
                    quantidadeSugeridaG = null,
                    quantidadeSugeridaCompra = null,
                    unidadeExibicao = null,
                    motivoDadosInsuficientes = motivoInsuficiente
                )
            } else {
                val demandaMedia = totalG!!.divide(BigDecimal(3), 6, RoundingMode.HALF_UP)
                val consumoDiario = totalG.divide(BigDecimal(diasHistorico), 8, RoundingMode.HALF_UP)
                val saldoProjetado = BigDecimal(estoqueValidoNoProximoMes[ingredienteId] ?: 0)
                    .subtract(consumoDiario * BigDecimal(diasAteProximoMes))
                    .max(BigDecimal.ZERO)
                CalculoPrevisaoCompras.item(
                    ingredienteId = ingredienteId,
                    nome = ingrediente[IngredientesTable.nome],
                    unidade = unidade,
                    pesoPorUnidadeG = pesoPorUnidadeG,
                    estoqueAtualG = estoqueAtual[ingredienteId] ?: 0,
                    estoqueProjetadoG = saldoProjetado,
                    demandaMediaG = demandaMedia
                )
            }
        }.sortedWith(compareBy({ CalculoPrevisaoCompras.ordem(it.faixa) }, { it.ingredienteNome }))

        PrevisaoComprasResponse(
            periodoHistoricoInicio = inicioHistorico.toString(),
            periodoHistoricoFim = fimHistorico.toString(),
            proximoMes = YearMonth.from(inicioProximoMes).toString(),
            resumo = ResumoPrevisaoComprasDto(
                urgentes = itens.count { it.faixa == FaixaCobertura.URGENTE },
                atencao = itens.count { it.faixa == FaixaCobertura.ATENCAO },
                seguros = itens.count { it.faixa == FaixaCobertura.SEGURO },
                dadosInsuficientes = itens.count { it.faixa == FaixaCobertura.DADOS_INSUFICIENTES }
            ),
            itens = itens,
            produtosSemFichaTecnica = produtosSemFicha
        )
    }
}

object CalculoPrevisaoCompras {
    fun item(
        ingredienteId: Long,
        nome: String,
        unidade: String,
        pesoPorUnidadeG: Int,
        estoqueAtualG: Int,
        estoqueProjetadoG: BigDecimal,
        demandaMediaG: BigDecimal
    ): PrevisaoCompraItemDto {
        require(demandaMediaG > BigDecimal.ZERO)

        val coberturaExata = BigDecimal(estoqueAtualG).multiply(BigDecimal(100))
            .divide(demandaMediaG, 8, RoundingMode.HALF_UP)
        val faixa = when {
            coberturaExata >= BigDecimal(100) -> FaixaCobertura.SEGURO
            coberturaExata >= BigDecimal(50) -> FaixaCobertura.ATENCAO
            else -> FaixaCobertura.URGENTE
        }
        val faltaG = demandaMediaG.subtract(BigDecimal(estoqueAtualG)).max(BigDecimal.ZERO)
        val gramasPorUnidade = BigDecimal(ConversaoEstoque.gramasPorUnidade(unidade, pesoPorUnidadeG))
        val unidadeExibicao = when (unidade.trim().lowercase()) {
            "kg" -> "kg"
            "g" -> "g"
            "un" -> "un"
            "l" -> "L"
            else -> unidade
        }
        return PrevisaoCompraItemDto(
            ingredienteId = ingredienteId,
            ingredienteNome = nome,
            unidadeCompra = unidade,
            estoqueAtualG = estoqueAtualG,
            estoqueProjetadoInicioMesG = estoqueProjetadoG.decimal(),
            demandaMediaMensalG = demandaMediaG.decimal(),
            coberturaPct = coberturaExata.decimal(),
            faixa = faixa,
            quantidadeSugeridaG = faltaG.decimal(),
            quantidadeSugeridaCompra = faltaG.divide(gramasPorUnidade, 3, RoundingMode.HALF_UP).toDouble(),
            unidadeExibicao = unidadeExibicao
        )
    }

    fun ordem(faixa: FaixaCobertura): Int = when (faixa) {
        FaixaCobertura.URGENTE -> 0
        FaixaCobertura.ATENCAO -> 1
        FaixaCobertura.SEGURO -> 2
        FaixaCobertura.DADOS_INSUFICIENTES -> 3
    }

    private fun BigDecimal.decimal(): Double = setScale(2, RoundingMode.HALF_UP).toDouble()
}
