package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.*
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DashboardService(
    private val restauranteId: Long,
    private val relogio: Clock = Clock.system(FUSO_FORTALEZA)
) {

    companion object {
        val FUSO_FORTALEZA: ZoneId = ZoneId.of("America/Fortaleza")
    }

    suspend fun obterKpis(): DashboardKpisDto = dbQuery {
        val hoje = LocalDate.now(relogio)
        val agora = relogio.instant()
        val periodos = CalculoDashboard.periodosMtd(hoje)
        val horarioFortaleza = ZonedDateTime.ofInstant(agora, FUSO_FORTALEZA).toLocalTime()
        val fimComparativo = periodos.anterior?.let {
            ZonedDateTime.of(it.fim, horarioFortaleza, FUSO_FORTALEZA).toInstant()
        }
        val vendasDaLoja = VendasTable
            .selectAll()
            .where { VendasTable.restauranteId eq restauranteId }
            .toList()

        val vendasAtuais = vendasDaLoja.filter { venda ->
            dataVenda(venda) in periodos.atual.inicio..periodos.atual.fim && vendaAte(venda, agora)
        }
        val vendasAnteriores = periodos.anterior?.let { anterior ->
            vendasDaLoja.filter { venda ->
                dataVenda(venda) in anterior.inicio..anterior.fim && vendaAte(venda, fimComparativo!!)
            }
        }.orEmpty()

        val faturamentoAtual = vendasAtuais.sumOf { it[VendasTable.valorTotal] }.dinheiro()
        val faturamentoAnterior = vendasAnteriores.sumOf { it[VendasTable.valorTotal] }.dinheiro()
        val totalPedidos = vendasAtuais.size
        val pedidosAnteriores = vendasAnteriores.size

        val ticketMedio = CalculoDashboard.ticketMedio(faturamentoAtual, totalPedidos)
        val ticketAnterior = CalculoDashboard.ticketMedio(faturamentoAnterior, pedidosAnteriores)

        val totalProdutosVendidos = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where {
                (VendasTable.restauranteId eq restauranteId) and
                    (VendasTable.dataVenda greaterEq periodos.atual.inicio.toKotlinLocalDate()) and
                    (VendasTable.dataVenda lessEq periodos.atual.fim.toKotlinLocalDate())
            }
            .filter { vendaAte(it, agora) }
            .sumOf { it[ItensVendaTable.quantidade] }

        val impactoAtual = obterImpacto(periodos.atual, agora)
        val impactoAnterior = periodos.anterior?.let { obterImpacto(it, fimComparativo!!) }
        val inicioSerie = hoje.minusDays(6)
        val serie = (0L..6L).map { deslocamento ->
            val data = inicioSerie.plusDays(deslocamento)
            val vendasDoDia = vendasDaLoja.filter {
                dataVenda(it) == data && vendaAte(it, agora)
            }
            SerieDiariaDashboardDto(
                data = data.toString(),
                totalVendas = vendasDoDia.sumOf { it[VendasTable.valorTotal] }.dinheiro().toDouble(),
                quantidadePedidos = vendasDoDia.size
            )
        }

        DashboardKpisDto(
            faturamentoTotal = faturamentoAtual.toDouble(),
            totalPedidos = totalPedidos,
            totalProdutosVendidos = totalProdutosVendidos,
            ticketMedio = ticketMedio.toDouble(),
            impactoFinanceiro = impactoAtual.dto(),
            variacoes = VariacoesDashboardDto(
                faturamentoPct = periodos.anterior?.let {
                    CalculoDashboard.variacaoPercentual(faturamentoAtual, faturamentoAnterior)
                },
                totalPedidosPct = periodos.anterior?.let {
                    CalculoDashboard.variacaoPercentual(BigDecimal(totalPedidos), BigDecimal(pedidosAnteriores))
                },
                ticketMedioPct = periodos.anterior?.let {
                    CalculoDashboard.variacaoPercentual(ticketMedio, ticketAnterior)
                },
                impactoLiquidoPct = impactoAnterior?.let {
                    CalculoDashboard.variacaoPercentual(impactoAtual.liquido, it.liquido)
                }
            ),
            periodoAtual = periodos.atual.dto(),
            periodoComparativo = periodos.anterior?.dto(),
            ultimosSeteDias = serie
        )
    }

    private fun obterImpacto(periodo: PeriodoCivil, fimInstante: Instant): ImpactoCalculado {
        val promocoesConfirmadas = PromocoesTable.selectAll()
            .where { PromocoesTable.restauranteId eq restauranteId }
            .filter { row ->
                dataConfirmacao(row)?.let(periodo::contem) == true && confirmacaoAte(row, fimInstante)
            }

        val valorSalvo = promocoesConfirmadas
            .filter { it[PromocoesTable.status] == StatusPromocao.CONCLUIDA_SUCESSO.name }
            .sumOf { it[PromocoesTable.valorSalvo] }

        val perdaPromocoes = promocoesConfirmadas
            .filter { it[PromocoesTable.status] == StatusPromocao.EXPIRADA_COM_PERDA.name }
            .sumOf { it[PromocoesTable.valorPerdido] ?: BigDecimal.ZERO }

        val perdaSemCampanha = (MovimentosEstoqueTable innerJoin LotesTable)
            .selectAll()
            .where {
                (MovimentosEstoqueTable.restauranteId eq restauranteId) and
                    MovimentosEstoqueTable.promocaoId.isNull()
            }
            .filter { row ->
                row[MovimentosEstoqueTable.tipo] in setOf("DESCARTE", "PERDA") &&
                    InstantesEstoque.dataCivil(row[MovimentosEstoqueTable.ocorridoEm]).let(periodo::contem) &&
                    Instant.parse(row[MovimentosEstoqueTable.ocorridoEm].toString()) <= fimInstante &&
                    LocalDate.parse(row[LotesTable.dataValidade].toString()) <
                    InstantesEstoque.dataCivil(row[MovimentosEstoqueTable.ocorridoEm])
            }
            .sumOf { row ->
                BigDecimal(row[MovimentosEstoqueTable.quantidadeG]) * row[LotesTable.custoUnitario]
            }

        return CalculoDashboard.impacto(valorSalvo, perdaPromocoes + perdaSemCampanha)
    }

    private fun dataConfirmacao(row: ResultRow): LocalDate? =
        row[PromocoesTable.confirmadaEm]?.let(InstantesEstoque::dataCivil)
            ?: row[PromocoesTable.dataConfirmacao]?.let { LocalDate.parse(it.toString()) }

    private fun confirmacaoAte(row: ResultRow, fim: Instant): Boolean =
        row[PromocoesTable.confirmadaEm]?.let { Instant.parse(it.toString()) <= fim } ?: true

    private fun dataVenda(row: ResultRow): LocalDate =
        row[VendasTable.dataVendaEm]?.let(InstantesEstoque::dataCivil)
            ?: LocalDate.parse(row[VendasTable.dataVenda].toString())

    private fun vendaAte(row: ResultRow, fim: Instant): Boolean =
        row[VendasTable.dataVendaEm]?.let { Instant.parse(it.toString()) <= fim } ?: true

    suspend fun obterDadosGraficos(): DashboardVendasResponse = dbQuery {
        val faturamentoDiario = obterFaturamentoDiarioInterno(30)
        val top5 = obterTop5MaisVendidosInterno()
        val baixoGiro = obterProdutosBaixoGiroInterno()

        DashboardVendasResponse(
            faturamentoDiario = faturamentoDiario,
            top5Produtos = top5,
            produtosBaixoGiro = baixoGiro
        )
    }

    suspend fun obterPrevisaoDemanda(): List<PrevisaoDemandaDto> = dbQuery {
        val hojeJava = LocalDate.now()
        val dataInicioKmp = hojeJava.minusDays(28).toKotlinLocalDate()
        val dataFimKmp = hojeJava.minusDays(1).toKotlinLocalDate()

        val itensVendaPeriodo = (ItensVendaTable innerJoin VendasTable innerJoin ProdutosTable)
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (VendasTable.restauranteId eq restauranteId) and ((VendasTable.dataVenda greaterEq dataInicioKmp) and (VendasTable.dataVenda lessEq dataFimKmp)) }
            .toList()

        val produtosAtivos = ProdutosTable
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .map { it[ProdutosTable.id] to it[ProdutosTable.nome] }

        val resultado = mutableListOf<PrevisaoDemandaDto>()

        val agrupado = itensVendaPeriodo
            .groupBy {
                val dataJava = LocalDate.parse(it[VendasTable.dataVenda].toString())
                val diaMysql = (dataJava.dayOfWeek.value % 7) + 1
                val prodId = it[ProdutosTable.id]
                prodId to diaMysql
            }
            .mapValues { (_, itens) ->
                itens.sumOf { it[ItensVendaTable.quantidade] }
            }

        for ((prodId, prodNome) in produtosAtivos) {
            for (dia in 1..7) {
                val totalVendido = agrupado[prodId to dia] ?: 0
                val mediaEsperada = (totalVendido / 4.0).roundTo(1)

                resultado.add(
                    PrevisaoDemandaDto(
                        produtoId = prodId,
                        produtoNome = prodNome,
                        diaSemana = dia,
                        totalVendidoPeriodo = totalVendido,
                        mediaVendasEsperada = mediaEsperada
                    )
                )
            }
        }

        resultado.sortedWith(compareBy({ it.produtoNome }, { it.diaSemana }))
    }

    private fun obterFaturamentoDiarioInterno(dias: Long): List<FaturamentoDiarioDto> {
        val dataInicioKmp = LocalDate.now().minusDays(dias).toKotlinLocalDate()

        val vendas = VendasTable
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and (VendasTable.dataVenda greaterEq dataInicioKmp) }
            .toList()

        return vendas
            .groupBy { it[VendasTable.dataVenda].toString() }
            .map { (data, vendasDoDia) ->
                FaturamentoDiarioDto(
                    dataVenda = data,
                    faturamento = vendasDoDia.sumOf { it[VendasTable.valorTotal].toDouble() }.roundTo(2),
                    totalPedidos = vendasDoDia.size
                )
            }
            .sortedBy { it.dataVenda }
    }

    private fun obterTop5MaisVendidosInterno(): List<ProdutoRankingDto> {
        val itens = (ItensVendaTable innerJoin ProdutosTable innerJoin VendasTable)
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (VendasTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .toList()

        return itens
            .groupBy { it[ProdutosTable.id] to it[ProdutosTable.nome] }
            .map { (key, itensDoProd) ->
                val (id, nome) = key
                val totalVendido = itensDoProd.sumOf { it[ItensVendaTable.quantidade] }
                val receitaTotal = itensDoProd.sumOf {
                    it[ItensVendaTable.quantidade] * it[ItensVendaTable.precoUnitario].toDouble()
                }.roundTo(2)

                ProdutoRankingDto(
                    id = id,
                    nome = nome,
                    totalVendido = totalVendido,
                    receitaTotal = receitaTotal
                )
            }
            .sortedByDescending { it.totalVendido }
            .take(5)
    }

    private fun obterProdutosBaixoGiroInterno(): List<ProdutoRankingDto> {
        val dataLimiteKmp = LocalDate.now().minusDays(15).toKotlinLocalDate()
        val todosProdutos = ProdutosTable
            .selectAll()
            .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.ativo eq true) }
            .map { it[ProdutosTable.id] to it[ProdutosTable.nome] }

        val itensRecentes = (ItensVendaTable innerJoin VendasTable)
            .selectAll()
            .where { (VendasTable.restauranteId eq restauranteId) and (VendasTable.dataVenda greaterEq dataLimiteKmp) }
            .toList()
            .groupBy { it[ItensVendaTable.produtoId] }

        return todosProdutos
            .map { (id, nome) ->
                val vendasProd = itensRecentes[id] ?: emptyList()
                val totalVendido = vendasProd.sumOf { it[ItensVendaTable.quantidade] }
                val receitaTotal = vendasProd.sumOf {
                    it[ItensVendaTable.quantidade] * it[ItensVendaTable.precoUnitario].toDouble()
                }.roundTo(2)

                ProdutoRankingDto(
                    id = id,
                    nome = nome,
                    totalVendido = totalVendido,
                    receitaTotal = receitaTotal
                )
            }
            .sortedBy { it.totalVendido }
            .take(5)
    }

    private fun Double.roundTo(decimals: Int): Double {
        return BigDecimal(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }
}

data class PeriodoCivil(val inicio: LocalDate, val fim: LocalDate) {
    fun contem(data: LocalDate): Boolean = data in inicio..fim
    fun dto() = PeriodoDashboardDto(inicio.toString(), fim.toString())
}

data class PeriodosMtd(val atual: PeriodoCivil, val anterior: PeriodoCivil?)

data class ImpactoCalculado(
    val salvo: BigDecimal,
    val perdido: BigDecimal,
    val liquido: BigDecimal,
    val economiaPct: BigDecimal
) {
    fun dto() = ImpactoFinanceiroDto(
        salvo.toDouble(), perdido.toDouble(), liquido.toDouble(), economiaPct.toDouble()
    )
}

object CalculoDashboard {
    fun periodosMtd(hoje: LocalDate): PeriodosMtd {
        val atual = PeriodoCivil(hoje.withDayOfMonth(1), hoje)
        val mesAnterior = hoje.minusMonths(1).withDayOfMonth(1)
        val anterior = if (hoje.dayOfMonth <= mesAnterior.lengthOfMonth()) {
            PeriodoCivil(mesAnterior, mesAnterior.withDayOfMonth(hoje.dayOfMonth))
        } else null
        return PeriodosMtd(atual, anterior)
    }

    fun ticketMedio(faturamento: BigDecimal, pedidos: Int): BigDecimal =
        if (pedidos == 0) BigDecimal.ZERO.dinheiro()
        else faturamento.divide(BigDecimal(pedidos), 2, RoundingMode.HALF_UP)

    fun impacto(valorSalvo: BigDecimal, valorPerdido: BigDecimal): ImpactoCalculado {
        val salvo = valorSalvo.dinheiro()
        val perdido = valorPerdido.dinheiro()
        val base = salvo + perdido
        val economia = if (base.signum() == 0) BigDecimal.ZERO
        else salvo.multiply(BigDecimal(100)).divide(base, 2, RoundingMode.HALF_UP)
        return ImpactoCalculado(salvo, perdido, (salvo - perdido).dinheiro(), economia)
    }

    fun variacaoPercentual(atual: BigDecimal, anterior: BigDecimal): Double? {
        if (anterior.signum() == 0) return if (atual.signum() == 0) 0.0 else null
        return atual.subtract(anterior)
            .multiply(BigDecimal(100))
            .divide(anterior.abs(), 2, RoundingMode.HALF_UP)
            .toDouble()
    }
}

private fun BigDecimal.dinheiro(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
