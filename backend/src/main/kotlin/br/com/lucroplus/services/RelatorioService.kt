package br.com.lucroplus.services

import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.models.DesperdicioHistoricoDto
import br.com.lucroplus.models.DesperdicioIngredienteDto
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month

class RelatorioService(private val restauranteId: Long) {

    suspend fun obterDesperdicioMesAtual(): List<DesperdicioIngredienteDto> = dbQuery {
        val hojeJava = LocalDate.now()
        val primeiroDiaMesJava = hojeJava.withDayOfMonth(1)

        val hojeKmp = hojeJava.toKotlinLocalDate()
        val primeiroDiaMesKmp = primeiroDiaMesJava.toKotlinLocalDate()

        val lotesVencidosMes = (LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where { (IngredientesTable.restauranteId eq restauranteId) and (LotesTable.restauranteId eq restauranteId) and ((LotesTable.dataValidade less hojeKmp) and (LotesTable.dataValidade greaterEq primeiroDiaMesKmp)) }
            .toList()

        lotesVencidosMes
            .groupBy { it[IngredientesTable.nome] to it[IngredientesTable.unidade] }
            .map { (key, lotesDoIngrediente) ->
                val (ingrediente, unidade) = key
                val quantidadePerdida = lotesDoIngrediente.sumOf { it[LotesTable.quantidadeG].toDouble() }.roundTo(3)
                val valorPerdidoRs = lotesDoIngrediente.sumOf {
                    it[LotesTable.quantidadeG].toDouble() * it[LotesTable.custoUnitario].toDouble()
                }.roundTo(2)

                DesperdicioIngredienteDto(
                    ingrediente = ingrediente,
                    quantidadePerdida = quantidadePerdida,
                    unidade = unidade,
                    valorPerdidoRs = valorPerdidoRs
                )
            }
            .sortedByDescending { it.valorPerdidoRs }
    }

    suspend fun obterHistoricoDesperdicio(): List<DesperdicioHistoricoDto> = dbQuery {
        val hojeJava = LocalDate.now()
        val seisMesesAtrasJava = hojeJava.minusMonths(5).withDayOfMonth(1)
        val seisMesesAtrasKmp = seisMesesAtrasJava.toKotlinLocalDate()
        val hojeKmp = hojeJava.toKotlinLocalDate()

        val lotesVencidos = LotesTable
            .selectAll()
            .where { (LotesTable.restauranteId eq restauranteId) and ((LotesTable.dataValidade less hojeKmp) and (LotesTable.dataValidade greaterEq seisMesesAtrasKmp)) }
            .toList()

        val perdasPorMes = lotesVencidos
            .groupBy {
                val data = LocalDate.parse(it[LotesTable.dataValidade].toString())
                data.year to data.month
            }
            .mapValues { (_, lotes) ->
                lotes.sumOf { it[LotesTable.quantidadeG].toDouble() * it[LotesTable.custoUnitario].toDouble() }.roundTo(2)
            }

        val resultado = mutableListOf<DesperdicioHistoricoDto>()
        for (i in 5 downTo 0) {
            val mesRef = hojeJava.minusMonths(i.toLong())
            val chave = mesRef.year to mesRef.month
            val valorPerdido = perdasPorMes[chave] ?: 0.0
            val label = "${mesRef.month.abreviacaoPtBr()}/${mesRef.year % 100}"

            resultado.add(
                DesperdicioHistoricoDto(
                    mesAno = label,
                    valorPerdidoRs = valorPerdido
                )
            )
        }

        resultado
    }

    private fun Month.abreviacaoPtBr(): String = when (this) {
        Month.JANUARY -> "Jan"
        Month.FEBRUARY -> "Fev"
        Month.MARCH -> "Mar"
        Month.APRIL -> "Abr"
        Month.MAY -> "Mai"
        Month.JUNE -> "Jun"
        Month.JULY -> "Jul"
        Month.AUGUST -> "Ago"
        Month.SEPTEMBER -> "Set"
        Month.OCTOBER -> "Out"
        Month.NOVEMBER -> "Nov"
        Month.DECEMBER -> "Dez"
    }

    private fun Double.roundTo(decimals: Int): Double {
        return BigDecimal(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }
}
