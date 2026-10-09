package br.com.lucroplus.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date

object UsuariosTable : Table("tb_usuario") {
    val id = long("id").autoIncrement()
    val nome = varchar("nome", 100)
    val email = varchar("email", 150).uniqueIndex()
    val senhaHash = varchar("senha_hash", 255)
    val tipo = varchar("tipo", 20)
    val ativo = bool("ativo").default(true)

    override val primaryKey = PrimaryKey(id)
}

object ProdutosTable : Table("tb_produto") {
    val id = long("id").autoIncrement()
    val nome = varchar("nome", 150)
    val descricao = text("descricao").nullable()
    val preco = decimal("preco", 8, 2)
    val categoria = varchar("categoria", 80)
    val ativo = bool("ativo").default(true)

    override val primaryKey = PrimaryKey(id)
}

object IngredientesTable : Table("tb_ingrediente") {
    val id = long("id").autoIncrement()
    val nome = varchar("nome", 100)
    val unidade = varchar("unidade", 20)
    val pesoPorUnidadeG = integer("peso_por_unidade_g").default(1000)
    val estoqueMinimo = decimal("estoque_minimo", 8, 3).default(0.0.toBigDecimal())

    override val primaryKey = PrimaryKey(id)
}

object LotesTable : Table("tb_lote") {
    val id = long("id").autoIncrement()
    val ingredienteId = long("ingrediente_id").references(IngredientesTable.id)
    val quantidadeG = integer("quantidade_g")
    val custoUnitario = decimal("custo_unitario", 8, 4)
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
    val id = long("id").autoIncrement()
    val usuarioId = long("usuario_id").references(UsuariosTable.id).nullable()
    val dataVenda = date("data_venda")
    val valorTotal = decimal("valor_total", 10, 2)
    val origem = varchar("origem", 20).default("importado")

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
    val id = long("id").autoIncrement()
    val produtoId = long("produto_id").references(ProdutosTable.id)
    val descontoPct = integer("desconto_pct").nullable() // Só o gerente define ao ativar.
    val motivo = text("motivo")                           // Ex: "Lote de Queijo vence em 3 dias"
    val status = varchar("status", 24)
    val dataSugestao = date("data_sugestao")
    val dataAtivacao = date("data_ativacao").nullable()
    // nullable caso a promocao nao esteja associada a um lote
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
