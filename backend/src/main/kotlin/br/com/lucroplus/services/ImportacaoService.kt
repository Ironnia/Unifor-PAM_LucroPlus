package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.ErroImportacaoDto
import br.com.lucroplus.models.ImportacaoResponse
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.time.LocalDate

class ImportacaoService(private val restauranteId: Long) {

    suspend fun importarProdutos(csvContent: String): ImportacaoResponse = dbQuery {
        val linhas = parseCsv(csvContent)
        if (linhas.isEmpty()) {
            return@dbQuery ImportacaoResponse(false, "O arquivo CSV está vazio.", 0, 0, 0)
        }

        val erros = mutableListOf<ErroImportacaoDto>()
        var linhasSucesso = 0

        val dados = linhas.drop(1)
        dados.forEachIndexed { index, colunas ->
            val numLinha = index + 2
            if (colunas.size < 4) {
                erros.add(ErroImportacaoDto(numLinha, "colunas", colunas.joinToString(","), "A linha deve conter 4 colunas (nome, descricao, preco, categoria)"))
                return@forEachIndexed
            }

            val nome = colunas[0].trim()
            val descricao = colunas[1].trim()
            val precoStr = colunas[2].trim().replace(",", ".")
            val categoria = colunas[3].trim()

            if (nome.isBlank()) {
                erros.add(ErroImportacaoDto(numLinha, "nome", nome, "O nome do produto não pode ser vazio"))
                return@forEachIndexed
            }

            val preco = precoStr.toBigDecimalOrNull()
            if (preco == null || preco <= BigDecimal.ZERO) {
                erros.add(ErroImportacaoDto(numLinha, "preco", precoStr, "Preço inválido. Deve ser um número maior que zero."))
                return@forEachIndexed
            }

            val existente = ProdutosTable
                .selectAll()
                .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.nome eq nome) }
                .singleOrNull()

            if (existente != null) {
                ProdutosTable.update({ ProdutosTable.id eq existente[ProdutosTable.id] }) {
                    it[ProdutosTable.descricao] = descricao
                    it[ProdutosTable.preco] = preco
                    it[ProdutosTable.categoria] = categoria
                    it[ProdutosTable.ativo] = true
                }
            } else {
                ProdutosTable.insert {
                    it[ProdutosTable.restauranteId] = this@ImportacaoService.restauranteId
                    it[ProdutosTable.nome] = nome
                    it[ProdutosTable.descricao] = descricao
                    it[ProdutosTable.preco] = preco
                    it[ProdutosTable.categoria] = categoria
                    it[ProdutosTable.ativo] = true
                }
            }

            linhasSucesso++
        }

        val total = dados.size
        ImportacaoResponse(
            sucesso = erros.isEmpty() || linhasSucesso > 0,
            mensagem = if (erros.isEmpty()) "Todos os $linhasSucesso produtos foram importados com sucesso!" else "$linhasSucesso produtos importados com ${erros.size} erros.",
            totalLinhas = total,
            linhasSucesso = linhasSucesso,
            linhasErro = erros.size,
            erros = erros
        )
    }

    suspend fun importarIngredientes(csvContent: String): ImportacaoResponse =
        importarIngredientesELotes(csvContent)

    suspend fun importarIngredientesELotes(csvContent: String): ImportacaoResponse = dbQuery {
        bloquearLoja()
        val linhas = parseCsv(csvContent)
        if (linhas.isEmpty()) return@dbQuery ImportacaoResponse(false, "O arquivo CSV está vazio.", 0, 0, 0)
        val erros = mutableListOf<ErroImportacaoDto>()
        var sucessos = 0
        val cabecalho = linhas.first().map { it.trim().lowercase() }
        linhas.drop(1).forEachIndexed { indice, c ->
            fun campo(nome: String): String? = cabecalho.indexOf(nome).takeIf { it >= 0 }
                ?.let { c.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) }
            try {
                require(c.size >= 6) { "Informe nome, unidade, quantidade, custo_unitario, data_validade e numero_lote" }
                val nome = c[0].trim()
                val unidade = c[1].trim().lowercase()
                require(nome.isNotBlank() && nome.length <= 100) { "Nome do ingrediente inválido" }
                val qtd = c[2].replace(",", ".").toBigDecimal()
                val preco = c[3].replace(",", ".").toBigDecimal()
                val validade = LocalDate.parse(c[4].trim())
                val numero = c[5].trim().ifBlank { null }
                require(numero == null || numero.length <= 50) { "Número do lote excede 50 caracteres" }
                val peso = campo("peso_por_unidade_g")?.toInt()
                val fator = ConversaoEstoque.gramasPorUnidade(unidade, peso)
                val gramas = ConversaoEstoque.quantidadeG(qtd, unidade, peso)
                val custo = ConversaoEstoque.custoPorGrama(preco, unidade, peso)
                val idOrigem = campo("id_externo")
                val compra = campo("compra_id_externo")
                val origem = campo("origem") ?: "csv_import"
                require(origem.length <= 20 && (idOrigem == null || idOrigem.length <= 100) &&
                    (compra == null || compra.length <= 100)) { "Identificador de origem muito longo" }
                val entradaTexto = campo("data_entrada")
                val entradaEm = entradaTexto?.takeIf { it.contains('T') }?.let(InstantesEstoque::parse)
                val entrada = entradaEm?.let(InstantesEstoque::dataCivil)
                    ?: entradaTexto?.let(LocalDate::parse) ?: LocalDate.now()
                require(!validade.isBefore(entrada)) { "Validade anterior à entrada" }

                val existente = idOrigem?.let { id -> LotesTable.selectAll().where {
                    (LotesTable.restauranteId eq restauranteId) and (LotesTable.origem eq origem) and
                        (LotesTable.idExterno eq id)
                }.singleOrNull() }
                if (existente != null) {
                    val ingrediente = IngredientesTable.selectAll().where {
                        IngredientesTable.id eq existente[LotesTable.ingredienteId]
                    }.single()
                    require(ingrediente[IngredientesTable.nome] == nome &&
                        existente[LotesTable.quantidadeInicialG] == gramas &&
                        existente[LotesTable.custoUnitario].compareTo(custo) == 0 &&
                        existente[LotesTable.dataValidade].toString() == validade.toString() &&
                        existente[LotesTable.dataEntrada].toString() == entrada.toString() &&
                        existente[LotesTable.entradaEm] == entradaEm &&
                        existente[LotesTable.compraIdExterno] == compra &&
                        existente[LotesTable.numeroLote] == numero &&
                        existente[LotesTable.unidadeOrigem] == unidade &&
                        existente[LotesTable.gramasPorUnidadeOrigem] == fator) {
                        "ID de compra já importado com dados diferentes"
                    }
                    sucessos++
                    return@forEachIndexed
                }
                val ingrediente = IngredientesTable.selectAll().where {
                    (IngredientesTable.restauranteId eq restauranteId) and (IngredientesTable.nome eq nome)
                }.singleOrNull()

                if (ingrediente != null && unidade in listOf("un", "l")) require(
                    ingrediente[IngredientesTable.pesoPorUnidadeG] == fator
                ) { "Equivalência difere da cadastrada para o ingrediente" }
                val ingredienteId = ingrediente?.get(IngredientesTable.id) ?: IngredientesTable.insert {
                    it[IngredientesTable.restauranteId] = this@ImportacaoService.restauranteId
                    it[IngredientesTable.nome] = nome; it[IngredientesTable.unidade] = unidade
                    it[IngredientesTable.pesoPorUnidadeG] = fator
                    it[IngredientesTable.estoqueMinimo] = BigDecimal.ZERO
                }[IngredientesTable.id]

                require(!MovimentosEstoqueTable.selectAll().where {
                    (MovimentosEstoqueTable.restauranteId eq restauranteId) and
                        (MovimentosEstoqueTable.ingredienteId eq ingredienteId)
                }.forUpdate().any { entradaEm == null || it[MovimentosEstoqueTable.ocorridoEm] >= entradaEm }) {
                    "Compra retroativa após movimentos; use uma base de ensaio nova"
                }
                LotesTable.insert {
                    it[LotesTable.restauranteId] = this@ImportacaoService.restauranteId
                    it[LotesTable.ingredienteId] = ingredienteId
                    it[LotesTable.quantidadeG] = gramas; it[LotesTable.quantidadeInicialG] = gramas
                    it[LotesTable.custoUnitario] = custo
                    it[LotesTable.quantidadeOrigem] = qtd; it[LotesTable.unidadeOrigem] = unidade
                    it[LotesTable.custoUnidadeOrigem] = preco; it[LotesTable.gramasPorUnidadeOrigem] = fator
                    it[LotesTable.origem] = origem; it[LotesTable.idExterno] = idOrigem
                    it[LotesTable.compraIdExterno] = compra
                    it[LotesTable.dataValidade] = validade.toKotlinLocalDate()
                    it[LotesTable.dataEntrada] = entrada.toKotlinLocalDate()
                    it[LotesTable.entradaEm] = entradaEm; it[LotesTable.importadoEm] = InstantesEstoque.agora()
                    it[LotesTable.numeroLote] = numero
                }
                sucessos++
            } catch (e: IllegalArgumentException) {
                erros += ErroImportacaoDto(indice + 2, "compra", c.joinToString(";"), e.message ?: "Compra inválida")
            } catch (e: java.time.format.DateTimeParseException) {
                erros += ErroImportacaoDto(indice + 2, "data", "", "Data inválida; use ISO 8601")
            }
        }
        ImportacaoResponse(erros.isEmpty() || sucessos > 0, "$sucessos lotes processados, ${erros.size} erros.",
            linhas.size - 1, sucessos, erros.size, erros)
    }

    suspend fun importarFichaTecnica(csvContent: String): ImportacaoResponse = dbQuery {
        val linhas = parseCsv(csvContent)
        if (linhas.isEmpty()) {
            return@dbQuery ImportacaoResponse(false, "O arquivo CSV está vazio.", 0, 0, 0)
        }

        val erros = mutableListOf<ErroImportacaoDto>()
        var linhasSucesso = 0

        val dados = linhas.drop(1)
        dados.forEachIndexed { index, colunas ->
            val numLinha = index + 2
            if (colunas.size < 4) {
                erros.add(ErroImportacaoDto(numLinha, "colunas", colunas.joinToString(","), "Linha deve conter 4 colunas (nome_produto, nome_ingrediente, quantidade_usada, unidade)"))
                return@forEachIndexed
            }

            val nomeProduto = colunas[0].trim()
            val nomeIngrediente = colunas[1].trim()
            val qtdStr = colunas[2].trim().replace(",", ".")
            val unidade = colunas[3].trim()

            val quantidadeUsada = qtdStr.toBigDecimalOrNull()
            if (quantidadeUsada == null || quantidadeUsada <= BigDecimal.ZERO) {
                erros.add(ErroImportacaoDto(numLinha, "quantidade_usada", qtdStr, "Quantidade usada inválida"))
                return@forEachIndexed
            }

            val produto = ProdutosTable
                .selectAll()
                .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.nome eq nomeProduto) }
                .singleOrNull()

            if (produto == null) {
                erros.add(ErroImportacaoDto(numLinha, "nome_produto", nomeProduto, "Produto '$nomeProduto' não encontrado no cardápio"))
                return@forEachIndexed
            }

            val ingrediente = IngredientesTable
                .selectAll()
                .where { (IngredientesTable.restauranteId eq restauranteId) and (IngredientesTable.nome eq nomeIngrediente) }
                .singleOrNull()

            if (ingrediente == null) {
                erros.add(ErroImportacaoDto(numLinha, "nome_ingrediente", nomeIngrediente, "Ingrediente '$nomeIngrediente' não cadastrado"))
                return@forEachIndexed
            }

            val produtoId = produto[ProdutosTable.id]
            val ingredienteId = ingrediente[IngredientesTable.id]

            val existeReceita = FichasTecnicasTable
                .selectAll()
                .where { (FichasTecnicasTable.produtoId eq produtoId) and (FichasTecnicasTable.ingredienteId eq ingredienteId) }
                .count() > 0

            if (existeReceita) {
                FichasTecnicasTable.update({ (FichasTecnicasTable.produtoId eq produtoId) and (FichasTecnicasTable.ingredienteId eq ingredienteId) }) {
                    it[FichasTecnicasTable.quantidadeUsada] = quantidadeUsada
                    it[FichasTecnicasTable.unidade] = unidade
                }
            } else {
                FichasTecnicasTable.insert {
                    it[FichasTecnicasTable.produtoId] = produtoId
                    it[FichasTecnicasTable.ingredienteId] = ingredienteId
                    it[FichasTecnicasTable.quantidadeUsada] = quantidadeUsada
                    it[FichasTecnicasTable.unidade] = unidade
                }
            }

            linhasSucesso++
        }

        val total = dados.size
        ImportacaoResponse(
            sucesso = erros.isEmpty() || linhasSucesso > 0,
            mensagem = if (erros.isEmpty()) "Todas as $linhasSucesso receitas da ficha técnica foram importadas!" else "$linhasSucesso receitas importadas com ${erros.size} erros.",
            totalLinhas = total,
            linhasSucesso = linhasSucesso,
            linhasErro = erros.size,
            erros = erros
        )
    }

    suspend fun importarVendas(csvContent: String): ImportacaoResponse {
        val linhas = parseCsv(csvContent)
        if (linhas.size < 2) return ImportacaoResponse(false, "O arquivo CSV está vazio.", 0, 0, 0)
        return try {
            dbQuery {
                bloquearLoja()
                val cabecalho = linhas.first().map { it.lowercase() }
                data class Linha(val venda: VendaPdv, val origem: String, val id: String, val produtoId: Long)
                val vendas = linhas.drop(1).mapIndexed { indice, c ->
                    fun campo(nome: String) = cabecalho.indexOf(nome).takeIf { it >= 0 }?.let { c.getOrNull(it) }
                    require(c.size >= 3) { "Linha ${indice + 2}: informe produto, horário e quantidade" }
                    val id = requireNotNull(campo("id_externo")?.takeIf { it.isNotBlank() }) { "Vendas exigem id_externo" }
                    val origem = campo("origem")?.takeIf { it.isNotBlank() } ?: "csv_import"
                    require(id.length <= 100 && origem.length <= 20) { "Identificador de origem muito longo" }
                    require(c[1].contains('T')) { "Vendas novas exigem horário original ISO 8601" }
                    val produto = ProdutosTable.selectAll().where {
                        (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.nome eq c[0])
                    }.singleOrNull() ?: throw IllegalArgumentException("Produto não cadastrado nesta loja")
                    val quantidade = c[2].toInt()
                    val preco = produto[ProdutosTable.preco]
                    Linha(VendaPdv((indice+1).toLong(), "", InstantesEstoque.parse(c[1]), quantidade,
                        preco, preco.multiply(BigDecimal(quantidade))), origem, id, produto[ProdutosTable.id])
                }.sortedWith(compareBy<Linha> { it.venda.em }.thenBy { it.id })
                for (linha in vendas) importarVendaNaTransacao(linha.venda, linha.produtoId, linha.origem, linha.id)
                ImportacaoResponse(true, "${vendas.size} vendas processadas com FIFO.", vendas.size, vendas.size, 0)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            falha(e, linhas.size-1)
        }
    }

    private val origemPdv = "pdv"

    private fun bloquearLoja() {
        require(RestaurantesTable.selectAll().where { RestaurantesTable.id eq restauranteId }
            .forUpdate().singleOrNull() != null) { "Restaurante não encontrado" }
    }

    private fun configuracoes() = ConfiguracoesRestauranteTable.selectAll().where {
        ConfiguracoesRestauranteTable.restauranteId eq restauranteId
    }.associate { it[ConfiguracoesRestauranteTable.chave] to it[ConfiguracoesRestauranteTable.valor] }

    private fun salvarControle(chave: String, valor: String) {
        ConfiguracoesRestauranteTable.upsert {
            it[ConfiguracoesRestauranteTable.restauranteId] = this@ImportacaoService.restauranteId
            it[ConfiguracoesRestauranteTable.chave] = chave; it[ConfiguracoesRestauranteTable.valor] = valor
        }
    }

    suspend fun statusPdv(): br.com.lucroplus.models.SincronizacaoDto = dbQuery {
        br.com.lucroplus.models.SincronizacaoDto(configuracoes()["pdv_ultimo_sucesso"])
    }

    suspend fun sincronizarPdv(): ImportacaoResponse {
        val inicioNanos = System.nanoTime()
        return try {
            dbQuery {
                bloquearLoja()
                val config = configuracoes()
                val configurado = config["pdv_url"]
                val modoDemo = configurado.isNullOrBlank() || configurado == "mock://pdv" || config["pdv_modo_demo"] == "true"
                val url = if (modoDemo) "mock://pdv" else configurado!!
                require(config["pdv_origem_importada"] == null || config["pdv_origem_importada"] == url) {
                    "Origem mudou após importação; use uma base de ensaio nova"
                }
                val dados = if (modoDemo) OrigemPdv.simularDemonstracao(restauranteId) else
                    OrigemPdv.ler(br.com.lucroplus.models.PdvConfigDto(url=url,
                        username=config["pdv_username"], password=config["pdv_password"]), restauranteId)
                val tipos = dados.produtos.associate { it.id to it.tipo }
                val ingredientes = IngredientesTable.selectAll().where { IngredientesTable.restauranteId eq restauranteId }
                    .filter { it[IngredientesTable.idExterno] != null }.associateBy { it[IngredientesTable.idExterno]!! }
                val pratos = ProdutosTable.selectAll().where { ProdutosTable.restauranteId eq restauranteId }
                    .filter { it[ProdutosTable.idExterno] != null }.associateBy { it[ProdutosTable.idExterno]!! }
                data class Evento(val em: kotlinx.datetime.Instant, val id: Long, val compra: CompraPdv? = null, val venda: VendaPdv? = null)
                val eventos = (dados.compras.map { Evento(it.em, it.id, compra=it) } +
                    dados.vendas.map { Evento(it.em, it.id, venda=it) })
                    .sortedWith(compareBy<Evento> { it.em }.thenBy { if (it.compra != null) 0 else 1 }.thenBy { it.id })
                var compras = 0; var vendas = 0; var movimentos = 0
                for (evento in eventos) {
                    evento.compra?.let { compra ->
                        require(tipos[compra.produto] == "INGREDIENTE") { "Compra do PDV deve ser de ingrediente" }
                        val ingrediente = ingredientes[compra.produto] ?: throw IllegalArgumentException("Ingrediente do PDV não mapeado nesta loja")
                        if (importarCompraNaTransacao(compra, ingrediente)) compras++
                    }
                    evento.venda?.let { venda ->
                        require(tipos[venda.produto] == "PRATO") { "Venda do PDV deve ser de prato" }
                        val produto = pratos[venda.produto] ?: throw IllegalArgumentException("Prato do PDV não mapeado nesta loja")
                        val (nova, baixas) = importarVendaNaTransacao(venda, produto[ProdutosTable.id], origemPdv, venda.id.toString())
                        if (nova) vendas++
                        movimentos += baixas
                    }
                }
                val conferencia = dados.posicoes.map { posicao ->
                    val lote = LotesTable.selectAll().where {
                        (LotesTable.restauranteId eq restauranteId) and (LotesTable.origem eq origemPdv) and
                            (LotesTable.idExterno eq posicao.lote)
                    }.singleOrNull() ?: throw IllegalArgumentException("Posição PDV sem compra correspondente")
                    require(ingredientes[posicao.produto]?.get(IngredientesTable.id) == lote[LotesTable.ingredienteId] &&
                        posicao.gramas >= 0 && posicao.corte >= lote[LotesTable.entradaEm]!!) { "Posição PDV inválida" }
                    val consumido = MovimentosEstoqueTable.selectAll().where {
                        (MovimentosEstoqueTable.loteId eq lote[LotesTable.id]) and
                            (MovimentosEstoqueTable.ocorridoEm lessEq posicao.corte)
                    }.sumOf { it[MovimentosEstoqueTable.quantidadeG].toLong() }
                    br.com.lucroplus.models.ConferenciaEstoqueDto(posicao.lote, InstantesEstoque.iso(posicao.corte),
                        posicao.gramas, Math.toIntExact(lote[LotesTable.quantidadeInicialG]!!.toLong()-consumido))
                }
                val agora = InstantesEstoque.iso(InstantesEstoque.agora())
                salvarControle("pdv_ultimo_sucesso", agora)
                salvarControle("pdv_origem_importada", url)

                SyncPdvHistoricoTable.insert {
                    it[SyncPdvHistoricoTable.restauranteId] = this@ImportacaoService.restauranteId
                    it[SyncPdvHistoricoTable.status] = "SUCESSO"
                    it[SyncPdvHistoricoTable.vendasImportadas] = vendas
                    it[SyncPdvHistoricoTable.comprasImportadas] = compras
                    it[SyncPdvHistoricoTable.movimentosGerados] = movimentos
                    it[SyncPdvHistoricoTable.tempoExecucaoMs] = tempoExecucaoMs(inicioNanos)
                    it[SyncPdvHistoricoTable.mensagem] = "Importação do PDV concluída."
                }
                ImportacaoResponse(true, "Importação do PDV concluída.", eventos.size, eventos.size, 0,
                    comprasNovas=compras, vendasNovas=vendas, movimentosNovos=movimentos,
                    ultimaSincronizacao=agora, conferenciaEstoque=conferencia)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e

            registrarFalhaPdv(mensagemSegura(e), tempoExecucaoMs(inicioNanos))
            falha(e).copy(ultimaSincronizacao=statusPdv().ultimaSincronizacao)
        }
    }

    private fun tempoExecucaoMs(inicioNanos: Long): Int =
        ((System.nanoTime() - inicioNanos) / 1_000_000L)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun mensagemSegura(e: Exception): String =
        if (e is IllegalArgumentException && e !is NumberFormatException) {
            e.message ?: "Dados inválidos na importação do PDV."
        } else {
            "Falha técnica durante a importação do PDV."
        }

    private suspend fun registrarFalhaPdv(motivo: String, tempoMs: Int) {
        try {
            dbQuery {
                SyncPdvHistoricoTable.insert {
                    it[SyncPdvHistoricoTable.restauranteId] = this@ImportacaoService.restauranteId
                    it[SyncPdvHistoricoTable.status] = "FALHA"
                    it[SyncPdvHistoricoTable.vendasImportadas] = 0
                    it[SyncPdvHistoricoTable.comprasImportadas] = 0
                    it[SyncPdvHistoricoTable.movimentosGerados] = 0
                    it[SyncPdvHistoricoTable.tempoExecucaoMs] = tempoMs
                    it[SyncPdvHistoricoTable.mensagem] = motivo
                }
            }
        } catch (_: Exception) {

        }
    }

    private fun falha(e: Exception, total: Int = 0): ImportacaoResponse {

        val motivo = mensagemSegura(e)
        return ImportacaoResponse(false, "Importação cancelada; nenhuma alteração parcial foi mantida.", total, 0, 1,
            listOf(ErroImportacaoDto(0, "importacao", "", motivo)))
    }

    private fun importarCompraNaTransacao(compra: CompraPdv, ingrediente: ResultRow): Boolean {
        require(compra.id > 0 && !compra.validade.isBefore(InstantesEstoque.dataCivil(compra.em))) { "Compra inválida" }
        val fator = ConversaoEstoque.gramasPorUnidade(compra.unidade, compra.fator)
        require(fator == ingrediente[IngredientesTable.pesoPorUnidadeG] || compra.unidade.lowercase() in listOf("g", "kg")) {
            "Equivalência da compra difere do ingrediente"
        }
        val gramas = ConversaoEstoque.quantidadeG(compra.quantidade, compra.unidade, compra.fator)
        val custo = ConversaoEstoque.custoPorGrama(compra.preco, compra.unidade, compra.fator)
        val existente = LotesTable.selectAll().where {
            (LotesTable.restauranteId eq restauranteId) and (LotesTable.origem eq origemPdv) and
                (LotesTable.idExterno eq compra.id.toString())
        }.forUpdate().singleOrNull()
        if (existente != null) {
            require(existente[LotesTable.ingredienteId] == ingrediente[IngredientesTable.id] &&
                existente[LotesTable.quantidadeInicialG] == gramas && existente[LotesTable.entradaEm] == compra.em &&
                existente[LotesTable.dataValidade].toString() == compra.validade.toString() &&
                existente[LotesTable.quantidadeOrigem]?.compareTo(compra.quantidade) == 0 &&
                existente[LotesTable.custoUnidadeOrigem]?.compareTo(compra.preco) == 0 &&
                existente[LotesTable.unidadeOrigem] == compra.unidade && existente[LotesTable.gramasPorUnidadeOrigem] == fator) {
                "Compra já importada com dados diferentes"
            }
            return false
        }
        require(!MovimentosEstoqueTable.selectAll().where {
            (MovimentosEstoqueTable.restauranteId eq restauranteId) and
                (MovimentosEstoqueTable.ingredienteId eq ingrediente[IngredientesTable.id]) and
                (MovimentosEstoqueTable.ocorridoEm greaterEq compra.em)
        }.forUpdate().any()) { "Compra retroativa após movimentos; use uma base de ensaio nova" }
        LotesTable.insert {
            it[restauranteId] = this@ImportacaoService.restauranteId; it[ingredienteId] = ingrediente[IngredientesTable.id]
            it[origem] = origemPdv; it[idExterno] = compra.id.toString(); it[compraIdExterno] = compra.id.toString()
            it[quantidadeG] = gramas; it[quantidadeInicialG] = gramas; it[custoUnitario] = custo
            it[quantidadeOrigem] = compra.quantidade; it[unidadeOrigem] = compra.unidade
            it[custoUnidadeOrigem] = compra.preco; it[gramasPorUnidadeOrigem] = fator
            it[entradaEm] = compra.em; it[importadoEm] = InstantesEstoque.agora()
            it[dataEntrada] = InstantesEstoque.dataCivil(compra.em).toKotlinLocalDate()
            it[dataValidade] = compra.validade.toKotlinLocalDate(); it[numeroLote] = compra.id.toString()
        }
        return true
    }

    private fun importarVendaNaTransacao(venda: VendaPdv, produto: Long, origem: String, id: String): Pair<Boolean, Int> {
        require(venda.quantidade > 0 && venda.preco >= BigDecimal.ZERO &&
            venda.total.compareTo(venda.preco.multiply(BigDecimal(venda.quantidade))) == 0) { "Quantidade ou valor de venda inválido" }
        val existente = VendasTable.selectAll().where {
            (VendasTable.restauranteId eq restauranteId) and (VendasTable.origem eq origem) and (VendasTable.idExterno eq id)
        }.forUpdate().singleOrNull()
        val vendaId = if (existente != null) {
            val item = ItensVendaTable.selectAll().where { ItensVendaTable.vendaId eq existente[VendasTable.id] }.single()
            require(existente[VendasTable.dataVendaEm] == venda.em && existente[VendasTable.valorTotal].compareTo(venda.total) == 0 &&
                item[ItensVendaTable.produtoId] == produto && item[ItensVendaTable.quantidade] == venda.quantidade &&
                item[ItensVendaTable.precoUnitario].compareTo(venda.preco) == 0) { "Venda já importada com dados diferentes" }
            existente[VendasTable.id]
        } else {
            VendasTable.insert {
                it[restauranteId] = this@ImportacaoService.restauranteId; it[idExterno] = id; it[VendasTable.origem] = origem
                it[dataVendaEm] = venda.em; it[dataVenda] = InstantesEstoque.dataCivil(venda.em).toKotlinLocalDate()
                it[importadoEm] = InstantesEstoque.agora(); it[valorTotal] = venda.total
            }[VendasTable.id].also { novo ->
                ItensVendaTable.insert {
                    it[vendaId] = novo; it[produtoId] = produto; it[quantidade] = venda.quantidade; it[precoUnitario] = venda.preco
                }
            }
        }
        val anteriores = MovimentosEstoqueTable.selectAll().where {
            MovimentosEstoqueTable.itemVendaId inSubQuery ItensVendaTable.select(ItensVendaTable.id).where { ItensVendaTable.vendaId eq vendaId }
        }.count().toInt()
        val baixas = MovimentoEstoqueService(restauranteId).consumirVendaNaTransacao(vendaId)
        return (existente == null) to (baixas.size-anteriores)
    }

    private fun parseCsv(content: String): List<List<String>> {
        return content
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { linha ->
                val delimiter = if (linha.contains(";")) ";" else ","
                linha.split(delimiter).map { it.trim().removeSurrounding("\"") }
            }
    }
}
