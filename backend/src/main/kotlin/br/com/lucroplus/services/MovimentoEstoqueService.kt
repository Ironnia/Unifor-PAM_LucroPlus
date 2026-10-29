package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

data class LoteFifo(val id: Long, val saldoG: Int, val entradaEm: Instant)
data class BaixaFifo(val loteId: Long, val quantidadeG: Int)
data class ConsumoEstoque(
    val id: Long, val itemVendaId: Long, val loteId: Long, val ingredienteId: Long,
    val quantidadeG: Int, val ocorridoEm: Instant
)

class ErroEstoque(message: String) : IllegalArgumentException(message)

object DistribuicaoFifo {
    fun distribuir(quantidadeG: Int, lotes: List<LoteFifo>): List<BaixaFifo> {
        require(quantidadeG > 0) { "Consumo deve ser positivo" }
        require(lotes.all { it.saldoG >= 0 } && lotes.map { it.id }.distinct().size == lotes.size)
        var falta = quantidadeG
        val baixas = mutableListOf<BaixaFifo>()
        for (lote in lotes.sortedWith(compareBy<LoteFifo> { it.entradaEm })) {
            val retirada = minOf(falta, lote.saldoG)
            if (retirada > 0) baixas += BaixaFifo(lote.id, retirada)
            falta -= retirada
            if (falta == 0) break
        }
        if (falta > 0) throw ErroEstoque("Estoque insuficiente: faltam $falta g")
        return baixas
    }
}

class MovimentoEstoqueService(private val restauranteId: Long) {
    init { require(restauranteId > 0) }

    suspend fun processarVenda(vendaId: Long): List<ConsumoEstoque> = dbQuery {
        consumirVendaNaTransacao(vendaId)
    }

    fun consumirVendaNaTransacao(vendaId: Long): List<ConsumoEstoque> {
        TransactionManager.current()
        val venda = VendasTable.selectAll().where {
            (VendasTable.id eq vendaId) and (VendasTable.restauranteId eq restauranteId)
        }.forUpdate().singleOrNull() ?: throw ErroEstoque("Venda não encontrada nesta loja")
        val itens = ItensVendaTable.selectAll().where { ItensVendaTable.vendaId eq vendaId }
            .orderBy(ItensVendaTable.id).forUpdate().toList()
        if (itens.isEmpty()) throw ErroEstoque("Venda sem itens")

        val existentes = MovimentosEstoqueTable.selectAll().where {
            (MovimentosEstoqueTable.itemVendaId inList itens.map { it[ItensVendaTable.id] }) and
                (MovimentosEstoqueTable.tipo eq "CONSUMO_VENDA")
        }.orderBy(MovimentosEstoqueTable.id).forUpdate().toList()
        if (existentes.isNotEmpty()) return existentes.map(::consumo)

        val instante = venda[VendasTable.dataVendaEm] ?: throw ErroEstoque("Venda sem horário original")
        val dia = InstantesEstoque.dataCivil(instante).toKotlinLocalDate()
        if (venda[VendasTable.dataVenda] != dia) throw ErroEstoque("Dia e horário da venda incompatíveis")
        data class Demanda(val itemId: Long, val ingredienteId: Long, val gramas: Int)
        val demandas = itens.flatMap { item ->
            val produto = ProdutosTable.selectAll().where {
                (ProdutosTable.id eq item[ItensVendaTable.produtoId]) and
                    (ProdutosTable.restauranteId eq restauranteId)
            }.singleOrNull() ?: throw ErroEstoque("Produto de outra loja")
            if (item[ItensVendaTable.quantidade] <= 0) throw ErroEstoque("Quantidade vendida inválida")
            val ficha = FichasTecnicasTable.selectAll().where {
                FichasTecnicasTable.produtoId eq produto[ProdutosTable.id]
            }.toList()
            if (ficha.isEmpty()) throw ErroEstoque("Produto sem ficha técnica")
            ficha.map { receita ->
                val ingrediente = IngredientesTable.selectAll().where {
                    (IngredientesTable.id eq receita[FichasTecnicasTable.ingredienteId]) and
                        (IngredientesTable.restauranteId eq restauranteId)
                }.singleOrNull() ?: throw ErroEstoque("Ingrediente de outra loja")
                val porPorcao = ConversaoEstoque.quantidadeG(receita[FichasTecnicasTable.quantidadeUsada],
                    receita[FichasTecnicasTable.unidade], ingrediente[IngredientesTable.pesoPorUnidadeG])
                val total = try { Math.multiplyExact(porPorcao, item[ItensVendaTable.quantidade]) }
                    catch (_: ArithmeticException) { throw ErroEstoque("Consumo excede o limite INTEGER") }
                Demanda(item[ItensVendaTable.id], ingrediente[IngredientesTable.id], total)
            }
        }
        val ingredientes = demandas.map { it.ingredienteId }.distinct()

        val lotes = LotesTable.selectAll().where {
            (LotesTable.restauranteId eq restauranteId) and
                (LotesTable.ingredienteId inList ingredientes) and (LotesTable.quantidadeG greater 0) and
                (LotesTable.entradaEm lessEq instante) and (LotesTable.dataValidade greater dia)
        }.orderBy(LotesTable.id).forUpdate().toList()

        if (MovimentosEstoqueTable.selectAll().where {
            (MovimentosEstoqueTable.restauranteId eq restauranteId) and
                (MovimentosEstoqueTable.ingredienteId inList ingredientes) and
                (MovimentosEstoqueTable.ocorridoEm greater instante)
        }.forUpdate().any()) throw ErroEstoque("Venda anterior a movimentos já processados; importe em ordem temporal")

        val saldos = lotes.associate { it[LotesTable.id] to it[LotesTable.quantidadeG] }.toMutableMap()
        data class Planejado(val demanda: Demanda, val baixa: BaixaFifo)
        val plano = demandas.flatMap { demanda ->
            val candidatos = lotes.filter { it[LotesTable.ingredienteId] == demanda.ingredienteId }
                .map { LoteFifo(it[LotesTable.id], saldos.getValue(it[LotesTable.id]), it[LotesTable.entradaEm]!!) }
            DistribuicaoFifo.distribuir(demanda.gramas, candidatos).map { baixa ->
                saldos[baixa.loteId] = saldos.getValue(baixa.loteId) - baixa.quantidadeG
                Planejado(demanda, baixa)
            }
        }

        val movimentos = plano.map { (demanda, baixa) ->
            val id = MovimentosEstoqueTable.insert {
                it[MovimentosEstoqueTable.restauranteId] = this@MovimentoEstoqueService.restauranteId
                it[loteId] = baixa.loteId; it[ingredienteId] = demanda.ingredienteId
                it[itemVendaId] = demanda.itemId; it[tipo] = "CONSUMO_VENDA"
                it[quantidadeG] = baixa.quantidadeG; it[ocorridoEm] = instante
            }[MovimentosEstoqueTable.id]
            ConsumoEstoque(id, demanda.itemId, baixa.loteId, demanda.ingredienteId, baixa.quantidadeG, instante)
        }
        for (id in plano.map { it.baixa.loteId }.distinct().sorted()) {
            LotesTable.update({ (LotesTable.id eq id) and (LotesTable.restauranteId eq restauranteId) }) {
                it[quantidadeG] = saldos.getValue(id)
            }
        }
        return movimentos
    }

    private fun consumo(row: ResultRow) = ConsumoEstoque(row[MovimentosEstoqueTable.id],
        row[MovimentosEstoqueTable.itemVendaId]!!, row[MovimentosEstoqueTable.loteId],
        row[MovimentosEstoqueTable.ingredienteId], row[MovimentosEstoqueTable.quantidadeG],
        row[MovimentosEstoqueTable.ocorridoEm])
}
