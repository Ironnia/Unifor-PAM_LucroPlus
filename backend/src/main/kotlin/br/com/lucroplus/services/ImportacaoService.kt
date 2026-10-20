package br.com.lucroplus.services

import br.com.lucroplus.database.*
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.ErroImportacaoDto
import br.com.lucroplus.models.ImportacaoResponse
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
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
        val linhas = parseCsv(csvContent)
        if (linhas.isEmpty()) {
            return@dbQuery ImportacaoResponse(false, "O arquivo CSV está vazio.", 0, 0, 0)
        }

        val erros = mutableListOf<ErroImportacaoDto>()
        var linhasSucesso = 0

        val dados = linhas.drop(1)
        dados.forEachIndexed { index, colunas ->
            val numLinha = index + 2
            if (colunas.size < 6) {
                erros.add(ErroImportacaoDto(numLinha, "colunas", colunas.joinToString(","), "Linha deve conter 6 colunas (nome, unidade, quantidade, custo_unitario, data_validade, numero_lote)"))
                return@forEachIndexed
            }

            val nome = colunas[0].trim()
            val unidade = colunas[1].trim()
            val qtdStr = colunas[2].trim().replace(",", ".")
            val custoStr = colunas[3].trim().replace(",", ".")
            val validadeStr = colunas[4].trim()
            val numeroLote = colunas.getOrNull(5)?.trim()?.ifBlank { null }

            val quantidade = qtdStr.toBigDecimalOrNull()
            val custo = custoStr.toBigDecimalOrNull()
            val dataValidade = try { LocalDate.parse(validadeStr).toKotlinLocalDate() } catch (e: Exception) { null }

            if (nome.isBlank()) {
                erros.add(ErroImportacaoDto(numLinha, "nome", nome, "Nome do ingrediente obrigatório"))
                return@forEachIndexed
            }
            if (quantidade == null || quantidade <= BigDecimal.ZERO) {
                erros.add(ErroImportacaoDto(numLinha, "quantidade", qtdStr, "Quantidade inválida"))
                return@forEachIndexed
            }
            if (custo == null || custo < BigDecimal.ZERO) {
                erros.add(ErroImportacaoDto(numLinha, "custo_unitario", custoStr, "Custo unitário inválido"))
                return@forEachIndexed
            }
            if (dataValidade == null) {
                erros.add(ErroImportacaoDto(numLinha, "data_validade", validadeStr, "Data de validade inválida (use YYYY-MM-DD)"))
                return@forEachIndexed
            }

            var ingrediente = IngredientesTable
                .selectAll()
                .where { (IngredientesTable.restauranteId eq restauranteId) and (IngredientesTable.nome eq nome) }
                .singleOrNull()

            val ingredienteId = if (ingrediente != null) {
                ingrediente[IngredientesTable.id]
            } else {
                IngredientesTable.insert {
                    it[IngredientesTable.restauranteId] = this@ImportacaoService.restauranteId
                    it[IngredientesTable.nome] = nome
                    it[IngredientesTable.unidade] = unidade
                    it[IngredientesTable.estoqueMinimo] = BigDecimal.ZERO
                }[IngredientesTable.id]
            }

            val hojeKmp = LocalDate.now().toKotlinLocalDate()
            LotesTable.insert {
                    it[LotesTable.restauranteId] = this@ImportacaoService.restauranteId
                it[LotesTable.ingredienteId] = ingredienteId
                it[LotesTable.quantidadeG] = quantidade.toInt()
                it[LotesTable.custoUnitario] = custo
                it[LotesTable.dataValidade] = dataValidade
                it[LotesTable.dataEntrada] = hojeKmp
                it[LotesTable.numeroLote] = numeroLote
            }

            linhasSucesso++
        }

        val total = dados.size
        ImportacaoResponse(
            sucesso = erros.isEmpty() || linhasSucesso > 0,
            mensagem = if (erros.isEmpty()) "Todos os $linhasSucesso lotes de ingredientes foram importados com sucesso!" else "$linhasSucesso lotes importados com ${erros.size} erros.",
            totalLinhas = total,
            linhasSucesso = linhasSucesso,
            linhasErro = erros.size,
            erros = erros
        )
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

    suspend fun importarVendas(csvContent: String): ImportacaoResponse = dbQuery {
        val linhas = parseCsv(csvContent)
        if (linhas.isEmpty()) {
            return@dbQuery ImportacaoResponse(false, "O arquivo CSV está vazio.", 0, 0, 0)
        }

        val erros = mutableListOf<ErroImportacaoDto>()
        var linhasSucesso = 0

        val dados = linhas.drop(1)
        dados.forEachIndexed { index, colunas ->
            val numLinha = index + 2
            if (colunas.size < 3) {
                erros.add(ErroImportacaoDto(numLinha, "colunas", colunas.joinToString(","), "Linha deve conter 3 colunas (nome_produto, data_venda, quantidade)"))
                return@forEachIndexed
            }

            val nomeProduto = colunas[0].trim()
            val dataStr = colunas[1].trim()
            val qtdStr = colunas[2].trim()

            val quantidade = qtdStr.toIntOrNull()
            val dataVenda = try { LocalDate.parse(dataStr.substringBefore('T')).toKotlinLocalDate() } catch (e: Exception) { null }

            if (quantidade == null || quantidade <= 0) {
                erros.add(ErroImportacaoDto(numLinha, "quantidade", qtdStr, "Quantidade de itens vendidos inválida"))
                return@forEachIndexed
            }
            if (dataVenda == null) {
                erros.add(ErroImportacaoDto(numLinha, "data_venda", dataStr, "Data da venda inválida (use YYYY-MM-DD)"))
                return@forEachIndexed
            }

            val produto = ProdutosTable
                .selectAll()
                .where { (ProdutosTable.restauranteId eq restauranteId) and (ProdutosTable.nome eq nomeProduto) }
                .singleOrNull()

            if (produto == null) {
                erros.add(ErroImportacaoDto(numLinha, "nome_produto", nomeProduto, "Produto '$nomeProduto' não cadastrado"))
                return@forEachIndexed
            }

            val produtoId = produto[ProdutosTable.id]
            val precoUnitario = produto[ProdutosTable.preco]
            val valorTotal = precoUnitario.multiply(BigDecimal(quantidade))

            val vendaId = VendasTable.insert {
                    it[VendasTable.restauranteId] = this@ImportacaoService.restauranteId
                it[VendasTable.usuarioId] = null
                it[VendasTable.dataVenda] = dataVenda
                it[VendasTable.valorTotal] = valorTotal
                it[VendasTable.origem] = "csv_import"
            }[VendasTable.id]

            ItensVendaTable.insert {
                it[ItensVendaTable.vendaId] = vendaId
                it[ItensVendaTable.produtoId] = produtoId
                it[ItensVendaTable.quantidade] = quantidade
                it[ItensVendaTable.precoUnitario] = precoUnitario
            }

            linhasSucesso++
        }

        val total = dados.size
        ImportacaoResponse(
            sucesso = erros.isEmpty() || linhasSucesso > 0,
            mensagem = if (erros.isEmpty()) "Todas as $linhasSucesso vendas foram importadas com sucesso!" else "$linhasSucesso vendas importadas com ${erros.size} erros.",
            totalLinhas = total,
            linhasSucesso = linhasSucesso,
            linhasErro = erros.size,
            erros = erros
        )
    }

    suspend fun sincronizarPdv(): ImportacaoResponse {
        val config = ConfiguracaoService(restauranteId).obterPdvConfig(incluirSenha = true)
        if (config.url?.contains("simulado", ignoreCase = true) == true || config.username.equals("mock", ignoreCase = true)) {
            return ImportacaoResponse(false, "A sincronização exige uma conexão MySQL real; use Testar conexão para a simulação.", 0, 0, 1)
        }
        val teste = ConfiguracaoService(restauranteId).testarConexao(config)

        return if (teste.sucesso) {
            ImportacaoResponse(
                sucesso = true,
                mensagem = "Conexão com o PDV validada; nenhum registro foi importado.",
                totalLinhas = 0,
                linhasSucesso = 0,
                linhasErro = 0
            )
        } else {
            ImportacaoResponse(
                sucesso = false,
                mensagem = "Falha ao sincronizar com o PDV: ${teste.mensagem}",
                totalLinhas = 0,
                linhasSucesso = 0,
                linhasErro = 1,
                erros = listOf(ErroImportacaoDto(1, "conexao", "", teste.mensagem))
            )
        }
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
