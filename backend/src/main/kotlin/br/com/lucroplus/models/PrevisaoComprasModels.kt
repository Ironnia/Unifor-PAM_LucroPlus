package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
enum class FaixaCobertura {
    URGENTE,
    ATENCAO,
    SEGURO,
    DADOS_INSUFICIENTES
}

@Serializable
data class ResumoPrevisaoComprasDto(
    val urgentes: Int,
    val atencao: Int,
    val seguros: Int,
    val dadosInsuficientes: Int
)

@Serializable
data class PrevisaoCompraItemDto(
    val ingredienteId: Long,
    val ingredienteNome: String,
    val unidadeCompra: String,
    val estoqueAtualG: Int,
    val estoqueProjetadoInicioMesG: Double?,
    val demandaMediaMensalG: Double?,
    val coberturaPct: Double?,
    val faixa: FaixaCobertura,
    val quantidadeSugeridaG: Double?,
    val quantidadeSugeridaCompra: Double?,
    val unidadeExibicao: String?,
    val motivoDadosInsuficientes: String? = null
)

@Serializable
data class PrevisaoComprasResponse(
    val periodoHistoricoInicio: String,
    val periodoHistoricoFim: String,
    val proximoMes: String,
    val resumo: ResumoPrevisaoComprasDto,
    val itens: List<PrevisaoCompraItemDto>,
    val produtosSemFichaTecnica: Int
)
