package br.com.lucroplus.services

import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.database.IngredientesTable
import br.com.lucroplus.database.LotesTable
import br.com.lucroplus.models.LoteItemDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.util.Locale

object LoteService {
    suspend fun listarLotes(): List<LoteItemDto> = dbQuery {
        val hojeJava = LocalDate.now()

        (LotesTable innerJoin IngredientesTable)
            .selectAll()
            .where { LotesTable.quantidadeG greater 0 }
            .orderBy(LotesTable.dataValidade, SortOrder.ASC)
            .map { row ->
                val id = row[LotesTable.id]
                val ingredienteId = row[LotesTable.ingredienteId]
                val ingredienteNome = row[IngredientesTable.nome]
                val unidade = row[IngredientesTable.unidade]
                val pesoPorUnidadeG = row[IngredientesTable.pesoPorUnidadeG]
                val quantidadeG = row[LotesTable.quantidadeG]
                val custoUnitario = row[LotesTable.custoUnitario].toDouble()
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
                    "l" -> String.format(Locale.US, "%.2f L", quantidadeG / 1000.0)
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
                    criticidade = criticidade
                )
            }
    }
}
