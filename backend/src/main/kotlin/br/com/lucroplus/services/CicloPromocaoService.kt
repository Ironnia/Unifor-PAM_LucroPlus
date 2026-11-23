package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.*
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class ErroPromocao(val codigo: Int, override val message: String) : RuntimeException(message)

interface PromocaoOperacoes {
    suspend fun criar(request: CriarPromocaoRequest): PromocaoDto
    suspend fun listar(status: StatusPromocao? = null): List<PromocaoDto>
    suspend fun ativar(id: Long, request: AtivarPromocaoRequest): PromocaoDto
    suspend fun recusar(id: Long): PromocaoDto
    suspend fun cancelar(id: Long): PromocaoDto
    suspend fun descartarPendente(loteId: Long)
    suspend fun confirmar(id: Long, request: ConfirmarPromocaoRequest): PromocaoDto
    suspend fun resumo(): ResumoPromocoesDto
}

class CicloPromocaoService(private val restauranteId: Long, private val hoje: () -> LocalDate = { LocalDate.now() }) : PromocaoOperacoes {
    override suspend fun criar(request: CriarPromocaoRequest): PromocaoDto = dbQuery {
        if (request.loteId <= 0 || request.pratoId <= 0) erro(400, "IDs devem ser positivos")
        val lote = travarLote(request.loteId)
        val existente = PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and ((PromocoesTable.loteId eq request.loteId) and (PromocoesTable.produtoId eq request.pratoId)) }.forUpdate().singleOrNull()

        if (existente != null) return@dbQuery dto(existente)
        validarLote(lote, request.pratoId)
        if (PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and ((PromocoesTable.loteId eq request.loteId) and
                    (PromocoesTable.status neq StatusPromocao.RECUSADA.name)) }.forUpdate().any()) erro(409, "O lote já possui uma promoção")

        val id = PromocoesTable.insert {
                    it[PromocoesTable.restauranteId] = this@CicloPromocaoService.restauranteId
            it[loteId] = request.loteId; it[produtoId] = request.pratoId
            it[descontoPct] = null; it[motivo] = "Promoção para o lote ${request.loteId} salvo pelo gerente"
            it[status] = StatusPromocao.SUGERIDA.name
            it[dataSugestao] = hoje().toKotlinLocalDate()
        }[PromocoesTable.id]
        dto(buscar(id))
    }

    override suspend fun listar(status: StatusPromocao?): List<PromocaoDto> = dbQuery {
        val consulta = PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.loteId.isNotNull()) }
        if (status != null) consulta.andWhere { PromocoesTable.status eq status.name }
        consulta.orderBy(PromocoesTable.id, SortOrder.DESC).map { dto(it) }
    }

    override suspend fun ativar(id: Long, request: AtivarPromocaoRequest): PromocaoDto = dbQuery {
        if (request.descontoPct !in 1..99) erro(400, "Desconto deve ser um inteiro entre 1 e 99")
        val inicio = data(request.dataInicio)
        val fim = data(request.dataFim)
        val (promocao, lote) = travarPromocao(id)
        if (promocao[PromocoesTable.status] == StatusPromocao.EM_ANDAMENTO.name) {
            if (promocao[PromocoesTable.descontoPct] == request.descontoPct &&
                promocao[PromocoesTable.dataInicio].toString() == inicio.toString() &&
                promocao[PromocoesTable.dataFim].toString() == fim.toString()) return@dbQuery dto(promocao)
            erro(409, "Promoção já ativada com outros dados")
        }
        exigirStatus(promocao, StatusPromocao.SUGERIDA)
        validarLote(lote, promocao[PromocoesTable.produtoId])
        val prazo = RegraValidadeLote.prazoLimiteVenda(LocalDate.parse(lote[LotesTable.dataValidade].toString()))
        if (inicio.isBefore(hoje()) || fim.isBefore(inicio) || fim.isAfter(prazo)) {
            erro(400, "Período deve começar hoje ou depois e terminar até o Prazo Limite de Venda do lote")
        }
        val valor = lote[LotesTable.custoUnitario].multiply(BigDecimal(lote[LotesTable.quantidadeG]))
            .setScale(2, RoundingMode.HALF_UP)
        PromocoesTable.update({ PromocoesTable.id eq id }) {
            it[status] = StatusPromocao.EM_ANDAMENTO.name
            it[descontoPct] = request.descontoPct
            it[dataAtivacao] = hoje().toKotlinLocalDate()
            it[dataInicio] = inicio.toKotlinLocalDate(); it[dataFim] = fim.toKotlinLocalDate()
            it[quantidadeReferenciaG] = lote[LotesTable.quantidadeG]
            it[valorEmRisco] = valor
        }
        dto(buscar(id))
    }

    override suspend fun recusar(id: Long): PromocaoDto = dbQuery {
        val (promocao, _) = travarPromocao(id)
        if (promocao[PromocoesTable.status] == StatusPromocao.RECUSADA.name) return@dbQuery dto(promocao)
        exigirStatus(promocao, StatusPromocao.SUGERIDA)
        PromocoesTable.update({ PromocoesTable.id eq id }) { it[status] = StatusPromocao.RECUSADA.name }
        dto(buscar(id))
    }

    override suspend fun cancelar(id: Long): PromocaoDto = dbQuery {
        val (promocao, _) = travarPromocao(id)
        if (promocao[PromocoesTable.status] == StatusPromocao.SUGERIDA.name) return@dbQuery dto(promocao)
        exigirStatus(promocao, StatusPromocao.EM_ANDAMENTO)
        PromocoesTable.update({ (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.id eq id) }) {
            it[status] = StatusPromocao.SUGERIDA.name
            it[descontoPct] = null
            it[dataAtivacao] = null
            it[dataInicio] = null
            it[dataFim] = null
            it[quantidadeReferenciaG] = null
            it[valorEmRisco] = null
            it[ativadaEm] = null
            it[inicioEfetivoEm] = null
            it[fimExclusivoEm] = null
            it[custoReferenciaPorGrama] = null
        }
        dto(buscar(id))
    }

    override suspend fun descartarPendente(loteId: Long): Unit = dbQuery {
        travarLote(loteId)
        val campanhas = PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.loteId eq loteId) }.forUpdate().toList()
        if (campanhas.any { it[PromocoesTable.status] !in listOf(StatusPromocao.SUGERIDA.name, StatusPromocao.RECUSADA.name) })
            erro(409, "Lote já possui promoção; use a ação da campanha")
        val acao = AcoesAlertaTable.selectAll().where { AcoesAlertaTable.loteId eq loteId }
            .forUpdate().singleOrNull() ?: erro(409, "Lote sem decisão registrada")
        if (acao[AcoesAlertaTable.acao] == AcaoAlerta.CIENTE.name) return@dbQuery
        PromocoesTable.update({ (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.loteId eq loteId) and (PromocoesTable.status eq StatusPromocao.SUGERIDA.name) }) {
            it[status] = StatusPromocao.RECUSADA.name
        }
        encerrarPendencia(loteId)
    }

    private fun encerrarPendencia(loteId: Long) {

        AcoesAlertaTable.update({ AcoesAlertaTable.loteId eq loteId }) { it[acao] = AcaoAlerta.CIENTE.name }
    }

    override suspend fun confirmar(id: Long, request: ConfirmarPromocaoRequest): PromocaoDto = dbQuery {
        val (promocao, _) = travarPromocao(id)
        val destino = if (request.loteEscoado) StatusPromocao.CONCLUIDA_SUCESSO else StatusPromocao.EXPIRADA_COM_PERDA
        if (promocao[PromocoesTable.status] == destino.name) return@dbQuery dto(promocao)
        exigirStatus(promocao, StatusPromocao.EM_ANDAMENTO)
        val fim = promocao[PromocoesTable.dataFim] ?: erro(409, "Promoção sem período registrado")

        if (!hoje().isAfter(LocalDate.parse(fim.toString()))) erro(409, "Aguarde o fim do período da promoção")
        val valor = promocao[PromocoesTable.valorEmRisco] ?: erro(409, "Promoção sem valor de referência")
        PromocoesTable.update({ PromocoesTable.id eq id }) {
            it[status] = destino.name
            it[dataConfirmacao] = hoje().toKotlinLocalDate()
            it[valorSalvo] = if (request.loteEscoado) valor else BigDecimal.ZERO
        }

        dto(buscar(id))
    }

    override suspend fun resumo(): ResumoPromocoesDto = dbQuery {
        val registros = PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.loteId.isNotNull()) }.toList()
        val inicioMes = hoje().withDayOfMonth(1)
        val salvosMes = registros.filter {
            it[PromocoesTable.status] == StatusPromocao.CONCLUIDA_SUCESSO.name &&
                it[PromocoesTable.dataConfirmacao]?.toString()?.let(LocalDate::parse)
                    ?.let { data -> !data.isBefore(inicioMes) && !data.isAfter(hoje()) } == true
        }.sumOf { it[PromocoesTable.valorSalvo] }
        val emAndamento = registros.filter { it[PromocoesTable.status] == StatusPromocao.EM_ANDAMENTO.name }
        ResumoPromocoesDto(
            emAndamento.size.toLong(),
            registros.filter { it[PromocoesTable.status] == StatusPromocao.CONCLUIDA_SUCESSO.name }
                .sumOf { it[PromocoesTable.valorSalvo] }.setScale(2).toPlainString(),
            valorSalvoMes = salvosMes.setScale(2, RoundingMode.HALF_UP).toDouble(),
            valorEmRiscoAtivo = emAndamento.sumOf { it[PromocoesTable.valorEmRisco] ?: BigDecimal.ZERO }
                .setScale(2, RoundingMode.HALF_UP).toDouble(),
            pendentes = (AcoesAlertaTable innerJoin LotesTable).selectAll().where {
                (LotesTable.restauranteId eq restauranteId) and (AcoesAlertaTable.acao eq AcaoAlerta.SALVAR_LOTE.name)
            }.map { it[LotesTable.id] }.distinct().count { loteId ->
                registros.none { it[PromocoesTable.loteId] == loteId && it[PromocoesTable.status] in
                    listOf(StatusPromocao.EM_ANDAMENTO.name, StatusPromocao.CONCLUIDA_SUCESSO.name, StatusPromocao.EXPIRADA_COM_PERDA.name) }
            }
        )
    }

    private fun validarLote(lote: ResultRow, pratoId: Long) {
        val prazo = RegraValidadeLote.prazoLimiteVenda(LocalDate.parse(lote[LotesTable.dataValidade].toString()))
        if (lote[LotesTable.quantidadeG] <= 0 || lote[LotesTable.custoUnitario] < BigDecimal.ZERO ||
            prazo.isBefore(hoje()) || prazo.isAfter(hoje().plusDays(14))) erro(409, "Lote fora da janela de promoção")
        val salvo = AcoesAlertaTable.selectAll().where {
            (AcoesAlertaTable.loteId eq lote[LotesTable.id]) and (AcoesAlertaTable.acao eq AcaoAlerta.SALVAR_LOTE.name)
        }.any()
        if (!salvo) erro(409, "O lote precisa estar marcado como Salvar Lote")
        val compativel = (FichasTecnicasTable innerJoin ProdutosTable).selectAll().where { (ProdutosTable.restauranteId eq restauranteId) and ((FichasTecnicasTable.ingredienteId eq lote[LotesTable.ingredienteId]) and
                (ProdutosTable.id eq pratoId) and (ProdutosTable.ativo eq true)) }.any()
        if (!compativel) erro(400, "Prato ativo deve utilizar o ingrediente do lote")
    }

    private fun travarLote(id: Long): ResultRow = LotesTable.selectAll().where { (LotesTable.restauranteId eq restauranteId) and (LotesTable.id eq id) }
        .forUpdate().singleOrNull() ?: erro(404, "Lote não encontrado")

    private fun buscar(id: Long): ResultRow = PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.id eq id) }
        .singleOrNull() ?: erro(404, "Promoção não encontrada")

    private fun travarPromocao(id: Long): Pair<ResultRow, ResultRow> {
        val loteId = buscar(id)[PromocoesTable.loteId]
            ?: erro(409, "Promoção legada sem lote: exige revisão manual")
        val lote = travarLote(loteId)
        val promocao = PromocoesTable.selectAll().where { (PromocoesTable.restauranteId eq restauranteId) and (PromocoesTable.id eq id) }.forUpdate().single()
        return promocao to lote
    }

    private fun exigirStatus(row: ResultRow, esperado: StatusPromocao) {
        if (row[PromocoesTable.status] != esperado.name) erro(409, "Transição inválida para o estado atual")
    }

    private fun dto(row: ResultRow): PromocaoDto {
        val prato = ProdutosTable.selectAll().where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.id eq row[PromocoesTable.produtoId]) }.single()
        val lote = (LotesTable innerJoin IngredientesTable).selectAll().where {
            (LotesTable.restauranteId eq restauranteId) and (IngredientesTable.restauranteId eq restauranteId) and
                (LotesTable.id eq row[PromocoesTable.loteId]!!)
        }.singleOrNull()
        val validade = lote?.let { LocalDate.parse(it[LotesTable.dataValidade].toString()) }
        val fim = row[PromocoesTable.dataFim]?.toString()
        return PromocaoDto(
            id = row[PromocoesTable.id], loteId = row[PromocoesTable.loteId]!!,
            pratoId = row[PromocoesTable.produtoId], pratoNome = prato[ProdutosTable.nome],
            status = StatusPromocao.valueOf(row[PromocoesTable.status]), descontoPct = row[PromocoesTable.descontoPct],
            dataSugestao = row[PromocoesTable.dataSugestao].toString(), dataAtivacao = row[PromocoesTable.dataAtivacao]?.toString(),
            dataInicio = row[PromocoesTable.dataInicio]?.toString(), dataFim = fim,
            dataConfirmacao = row[PromocoesTable.dataConfirmacao]?.toString(),
            quantidadeReferenciaG = row[PromocoesTable.quantidadeReferenciaG],
            valorEmRisco = row[PromocoesTable.valorEmRisco]?.setScale(2)?.toPlainString(),
            valorSalvo = row[PromocoesTable.valorSalvo].setScale(2).toPlainString(),
            aguardaConfirmacao = row[PromocoesTable.status] == StatusPromocao.EM_ANDAMENTO.name &&
                fim != null && hoje().isAfter(LocalDate.parse(fim)),
            ativadaEm = row[PromocoesTable.ativadaEm]?.let(InstantesEstoque::iso),
            inicioEfetivo = row[PromocoesTable.inicioEfetivoEm]?.let(InstantesEstoque::iso),
            fimExclusivo = row[PromocoesTable.fimExclusivoEm]?.let(InstantesEstoque::iso),
            custoReferenciaPorGrama = row[PromocoesTable.custoReferenciaPorGrama]?.toPlainString(),
            quantidadeConsumidaG = row[PromocoesTable.quantidadeConsumidaG],
            quantidadeDescartadaG = row[PromocoesTable.quantidadeDescartadaG],
            valorPerdido = row[PromocoesTable.valorPerdido]?.toPlainString(),
            confirmadaEm = row[PromocoesTable.confirmadaEm]?.let(InstantesEstoque::iso),
            confirmadaPor = row[PromocoesTable.confirmadaPor],
            ingredienteNome = lote?.get(IngredientesTable.nome),
            numeroLote = lote?.get(LotesTable.numeroLote)?.removePrefix("#"),
            prazoLimiteVenda = validade?.let(RegraValidadeLote::prazoLimiteVenda)?.toString(),
            criticidade = validade?.let { RegraValidadeLote.criticidade(RegraValidadeLote.diasAtePrazo(it, hoje())) }
        )
    }

    private fun data(valor: String): LocalDate = try { LocalDate.parse(valor) }
        catch (_: java.time.format.DateTimeParseException) { erro(400, "Data inválida; use AAAA-MM-DD") }

    private fun erro(codigo: Int, mensagem: String): Nothing = throw ErroPromocao(codigo, mensagem)
}
