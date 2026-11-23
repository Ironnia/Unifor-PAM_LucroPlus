package br.com.lucroplus.models

import kotlinx.serialization.Serializable

@Serializable
data class ErroImportacaoDto(
    val linha: Int,
    val campo: String,
    val valorRecebido: String,
    val motivo: String
)

@Serializable
data class ImportacaoResponse(
    val sucesso: Boolean,
    val mensagem: String,
    val totalLinhas: Int,
    val linhasSucesso: Int,
    val linhasErro: Int,
    val erros: List<ErroImportacaoDto> = emptyList(),
    val comprasNovas: Int = 0,
    val vendasNovas: Int = 0,
    val movimentosNovos: Int = 0,
    val ultimaSincronizacao: String? = null,
    val conferenciaEstoque: List<ConferenciaEstoqueDto> = emptyList()
)

@Serializable
data class ConferenciaEstoqueDto(val loteExterno: String, val corteEm: String,
    val quantidadePdvG: Int, val quantidadeInternaNoCorteG: Int) {
    val coincide: Boolean get() = quantidadePdvG == quantidadeInternaNoCorteG
}

@Serializable
data class SincronizacaoDto(val ultimaSincronizacao: String? = null)
