package br.com.lucroplus.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RestaurantesTable : Table("tb_restaurante") {
    val id = long("id").autoIncrement()
    val nome = varchar("nome", 150)
    override val primaryKey = PrimaryKey(id)
}

object UsuariosTable : Table("tb_usuario") {
    val id = long("id").autoIncrement()
    val nome = varchar("nome", 100)
    val email = varchar("email", 150).uniqueIndex()
    val senhaHash = varchar("senha_hash", 255)
    val tipo = varchar("tipo", 20)
    val ativo = bool("ativo").default(true)
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ProdutosTable : Table("tb_produto") {
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id).nullable()
    val idExterno = varchar("id_externo", 100).nullable()
    init { uniqueIndex("uq_produtos_restaurante_externo", restauranteId, idExterno) }
    val id = long("id").autoIncrement()
    val nome = varchar("nome", 150)
    val descricao = text("descricao").nullable()
    val preco = decimal("preco", 8, 2)
    val categoria = varchar("categoria", 80)
    val ativo = bool("ativo").default(true)

    override val primaryKey = PrimaryKey(id)
}

object IngredientesTable : Table("tb_ingrediente") {
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id).nullable()
    val idExterno = varchar("id_externo", 100).nullable()
    init { uniqueIndex("uq_ingredientes_restaurante_externo", restauranteId, idExterno) }
    val id = long("id").autoIncrement()
    val nome = varchar("nome", 100)
    val unidade = varchar("unidade", 20)
    val pesoPorUnidadeG = integer("peso_por_unidade_g").default(1000)
    val estoqueMinimo = decimal("estoque_minimo", 8, 3).default(0.0.toBigDecimal())

    override val primaryKey = PrimaryKey(id)
}

object LotesTable : Table("tb_lote") {
    val origem = varchar("origem", 20).nullable()
    val compraIdExterno = varchar("compra_id_externo", 100).nullable()

    val quantidadeInicialG = integer("quantidade_inicial_g").nullable()
    val quantidadeOrigem = decimal("quantidade_origem", 18, 6).nullable()
    val unidadeOrigem = varchar("unidade_origem", 20).nullable()
    val custoUnidadeOrigem = decimal("custo_unidade_origem", 18, 8).nullable()
    val gramasPorUnidadeOrigem = integer("gramas_por_unidade_origem").nullable()

    val entradaEm = timestamp("entrada_em").nullable()
    val importadoEm = timestamp("importado_em").nullable()
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id).nullable()
    val idExterno = varchar("id_externo", 100).nullable()
    init { uniqueIndex("uq_lotes_restaurante_origem_externo", restauranteId, origem, idExterno) }
    val id = long("id").autoIncrement()
    val ingredienteId = long("ingrediente_id").references(IngredientesTable.id)
    val quantidadeG = integer("quantidade_g")
    val custoUnitario = decimal("custo_unitario", 18, 8)
    val dataValidade = date("data_validade")
    val dataEntrada = date("data_entrada")
    val numeroLote = varchar("numero_lote", 50).nullable()
    val observacao = text("observacao").nullable()

    override val primaryKey = PrimaryKey(id)
}

object FichasTecnicasTable : Table("tb_ficha_tecnica") {
    val produtoId = long("produto_id").references(ProdutosTable.id)
    val ingredienteId = long("ingrediente_id").references(IngredientesTable.id)
    val quantidadeUsada = decimal("quantidade_usada", 10, 4)
    val unidade = varchar("unidade", 20)

    override val primaryKey = PrimaryKey(produtoId, ingredienteId)
}

object VendasTable : Table("tb_venda") {
    val origem = varchar("origem", 20).default("importado")
    val dataVendaEm = timestamp("data_venda_em").nullable()
    val importadoEm = timestamp("importado_em").nullable()
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id).nullable()
    val idExterno = varchar("id_externo", 100).nullable()
    init { uniqueIndex("uq_vendas_restaurante_origem_externo", restauranteId, origem, idExterno) }
    val id = long("id").autoIncrement()
    val usuarioId = long("usuario_id").references(UsuariosTable.id).nullable()
    val dataVenda = date("data_venda")
    val valorTotal = decimal("valor_total", 10, 2)

    override val primaryKey = PrimaryKey(id)
}

object ItensVendaTable : Table("tb_item_venda") {
    val id = long("id").autoIncrement()
    val vendaId = long("venda_id").references(VendasTable.id)
    val produtoId = long("produto_id").references(ProdutosTable.id)
    val quantidade = integer("quantidade")
    val precoUnitario = decimal("preco_unitario", 8, 2)

    override val primaryKey = PrimaryKey(id)
}

object PromocoesTable : Table("tb_promocao") {

    val ativadaEm = timestamp("ativada_em").nullable()
    val inicioEfetivoEm = timestamp("inicio_efetivo_em").nullable()
    val fimExclusivoEm = timestamp("fim_exclusivo_em").nullable()
    val custoReferenciaPorGrama = decimal("custo_referencia_por_grama", 18, 8).nullable()
    val quantidadeConsumidaG = integer("quantidade_consumida_g").nullable()
    val quantidadeDescartadaG = integer("quantidade_descartada_g").nullable()
    val valorPerdido = decimal("valor_perdido", 18, 2).nullable()
    val confirmadaEm = timestamp("confirmada_em").nullable()
    val confirmadaPor = long("confirmada_por").references(UsuariosTable.id).nullable()
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id).nullable()
    val id = long("id").autoIncrement()
    val produtoId = long("produto_id").references(ProdutosTable.id)
    val descontoPct = integer("desconto_pct").nullable()
    val motivo = text("motivo")
    val status = varchar("status", 24)
    val dataSugestao = date("data_sugestao")
    val dataAtivacao = date("data_ativacao").nullable()

    val loteId = long("lote_id").references(LotesTable.id).nullable()
    val dataInicio = date("data_inicio").nullable()
    val dataFim = date("data_fim").nullable()
    val dataConfirmacao = date("data_confirmacao").nullable()
    val quantidadeReferenciaG = integer("quantidade_referencia_g").nullable()
    val valorEmRisco = decimal("valor_em_risco", 18, 2).nullable()
    val valorSalvo = decimal("valor_salvo", 18, 2).default(java.math.BigDecimal.ZERO)

    init { uniqueIndex("uq_promocao_lote_produto", loteId, produtoId) }

    override val primaryKey = PrimaryKey(id)
}

object AlertasTable : Table("tb_alerta") {
    val id = long("id").autoIncrement()
    val loteId = long("lote_id").references(LotesTable.id)
    val tipo = varchar("tipo", 30)
    val mensagem = text("mensagem")
    val dataAlerta = date("data_alerta")
    val visualizado = bool("visualizado").default(false)

    init { uniqueIndex("uq_alerta_lote_tipo", loteId, tipo) }

    override val primaryKey = PrimaryKey(id)
}

object AcoesAlertaTable : Table("tb_acao_alerta") {
    val loteId = long("lote_id").references(LotesTable.id)
    val alertaId = long("alerta_id").references(AlertasTable.id).uniqueIndex()
    val acao = varchar("acao", 20)
    val dataAcao = date("data_acao")

    override val primaryKey = PrimaryKey(loteId)
}

object ConfiguracoesTable : Table("tb_configuracao") {
    val chave = varchar("chave", 100)
    val valor = varchar("valor", 255).nullable()

    override val primaryKey = PrimaryKey(chave)
}

object ConfiguracoesRestauranteTable : Table("tb_configuracao_restaurante") {
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id)
    val chave = varchar("chave", 100)
    val valor = varchar("valor", 255).nullable()
    override val primaryKey = PrimaryKey(restauranteId, chave)
}

object MovimentosEstoqueTable : Table("tb_movimento_estoque") {
    val id = long("id").autoIncrement()
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id)
    val loteId = long("lote_id").references(LotesTable.id)
    val ingredienteId = long("ingrediente_id").references(IngredientesTable.id)
    val tipo = varchar("tipo", 20)
    val quantidadeG = integer("quantidade_g").check("chk_movimento_quantidade") { it greater 0 }
    val ocorridoEm = timestamp("ocorrido_em")
    val itemVendaId = long("item_venda_id").references(ItensVendaTable.id).nullable()
    val promocaoId = long("promocao_id").references(PromocoesTable.id).nullable()

    init {
        uniqueIndex("uq_movimento_item_lote_tipo", itemVendaId, loteId, tipo)
        uniqueIndex("uq_movimento_descarte_promocao", promocaoId)
        index("idx_movimento_lote", false, loteId)
        index("idx_movimento_restaurante_data", false, restauranteId, ocorridoEm)
        index("idx_movimento_ingrediente", false, ingredienteId)
    }
    override val primaryKey = PrimaryKey(id)
}

object SyncPdvHistoricoTable : Table("tb_sync_pdv_historico") {
    val id = long("id").autoIncrement()
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id)
    val executadoEm = timestamp("executado_em").defaultExpression(CurrentTimestamp)
    val status = varchar("status", 20)
    val vendasImportadas = integer("vendas_importadas").default(0)
    val comprasImportadas = integer("compras_importadas").default(0)
    val movimentosGerados = integer("movimentos_gerados").default(0)
    val tempoExecucaoMs = integer("tempo_execucao_ms").nullable()
    val mensagem = text("mensagem").nullable()

    init { index("idx_sync_restaurante_data", false, restauranteId, executadoEm) }
    override val primaryKey = PrimaryKey(id)
}

object BcgHistoricoTable : Table("tb_bcg_historico") {
    val restauranteId = long("restaurante_id").references(RestaurantesTable.id)
    val produtoId = long("produto_id").references(ProdutosTable.id)
    val mesReferencia = varchar("mes_referencia", 7)
    val categoria = varchar("categoria", 24)

    override val primaryKey = PrimaryKey(restauranteId, produtoId, mesReferencia)
}
