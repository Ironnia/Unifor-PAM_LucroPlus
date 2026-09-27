package br.com.lucroplus.services

import br.com.lucroplus.database.ConfiguracoesTable
import br.com.lucroplus.database.DatabaseFactory.dbQuery
import br.com.lucroplus.models.PdvConfigDto
import br.com.lucroplus.models.TestarConexaoResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.sql.DriverManager

object ConfiguracaoService {

    private const val DEFAULT_JDBC_URL =
        "jdbc:mysql://localhost:3306/pdv_ficticio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"

    fun validarPdvConfig(config: PdvConfigDto): String? {
        val url = config.url?.trim().orEmpty()
        val host = config.host?.trim().orEmpty()
        val usuario = (config.usuario ?: config.username)?.trim().orEmpty()

        if (url.isBlank() && host.isBlank()) {
            return "Informe a URL JDBC ou o host do PDV"
        }
        if (url.isNotBlank() && !url.startsWith("jdbc:mysql://")) {
            return "A URL do PDV deve usar o formato jdbc:mysql://"
        }
        if (config.porta != null && config.porta !in 1..65535) {
            return "A porta do PDV deve estar entre 1 e 65535"
        }
        if (usuario.isBlank()) {
            return "Informe o usuário do banco do PDV"
        }

        return null
    }

    private fun montarJdbcUrl(config: PdvConfigDto): String {
        if (!config.url.isNullOrBlank()) {
            return config.url.trim()
        }

        val host = config.host?.trim().orEmpty()
        val porta = config.porta ?: 3306
        val banco = config.banco?.trim()?.ifBlank { "pdv_ficticio" } ?: "pdv_ficticio"
        return "jdbc:mysql://$host:$porta/$banco?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
    }

    suspend fun obterPdvConfig(): PdvConfigDto = dbQuery {
        val configs = ConfiguracoesTable
            .selectAll()
            .associate { it[ConfiguracoesTable.chave] to (it[ConfiguracoesTable.valor] ?: "") }

        val url = configs["pdv_url"]?.ifBlank { null } ?: DEFAULT_JDBC_URL
        val username = configs["pdv_username"]?.ifBlank { null } ?: "root"

        PdvConfigDto(
            url = url,
            username = username,
            password = null
        )
    }

    suspend fun salvarPdvConfig(config: PdvConfigDto): Unit = dbQuery {
        val url = montarJdbcUrl(config)
        val user = config.usuario ?: config.username ?: "root"
        val pass = config.senha ?: config.password ?: ""

        ConfiguracoesTable.upsert {
            it[chave] = "pdv_url"
            it[valor] = url
        }
        ConfiguracoesTable.upsert {
            it[chave] = "pdv_username"
            it[valor] = user
        }
        ConfiguracoesTable.upsert {
            it[chave] = "pdv_password"
            it[valor] = pass
        }
    }

    suspend fun testarConexao(config: PdvConfigDto): TestarConexaoResponse = withContext(Dispatchers.IO) {
        val erroValidacao = validarPdvConfig(config)
        if (erroValidacao != null) {
            return@withContext TestarConexaoResponse(
                sucesso = false,
                mensagem = erroValidacao
            )
        }

        val inicio = System.nanoTime()
        val jdbcUrl = montarJdbcUrl(config)
        val user = config.usuario ?: config.username ?: ""
        val pass = config.senha ?: config.password ?: ""

        if (jdbcUrl.contains("simulado", ignoreCase = true) || user.equals("mock", ignoreCase = true)) {
            return@withContext TestarConexaoResponse(
                sucesso = true,
                tempoRespostaMs = 50L,
                mensagem = "Conexão com o PDV simulado realizada com sucesso!",
                detalhes = "Ambiente de demonstração; nenhuma conexão externa foi aberta."
            )
        }

        try {
            DriverManager.setLoginTimeout(3)
            val isValid = DriverManager.getConnection(jdbcUrl, user, pass).use { connection ->
                connection.isValid(2)
            }
            val tempoMs = ((System.nanoTime() - inicio) / 1_000_000).coerceAtLeast(1L)

            if (isValid) {
                TestarConexaoResponse(
                    sucesso = true,
                    tempoRespostaMs = tempoMs,
                    mensagem = "Conexão JDBC estabelecida com sucesso! O banco do PDV está acessível."
                )
            } else {
                TestarConexaoResponse(
                    sucesso = false,
                    tempoRespostaMs = tempoMs,
                    mensagem = "Não foi possível validar a conexão com o banco do PDV."
                )
            }
        } catch (_: Exception) {
            val tempoMs = ((System.nanoTime() - inicio) / 1_000_000).coerceAtLeast(1L)
            TestarConexaoResponse(
                sucesso = false,
                tempoRespostaMs = tempoMs,
                mensagem = "Não foi possível conectar ao banco do PDV.",
                detalhes = "Verifique host, porta, banco, usuário, senha e disponibilidade da rede."
            )
        }
    }
}
