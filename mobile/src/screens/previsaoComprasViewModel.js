export const FaixaPrevisao = Object.freeze({
  URGENTE: 'URGENTE',
  ATENCAO: 'ATENCAO',
  SEGURO: 'SEGURO',
  DADOS_INSUFICIENTES: 'DADOS_INSUFICIENTES',
});

const FAIXAS = new Set(Object.values(FaixaPrevisao));

export function validarPrevisaoCompras(payload) {
  const resumo = payload?.resumo;
  if (!payload || typeof payload !== 'object'
    || typeof payload.periodoHistoricoInicio !== 'string'
    || typeof payload.periodoHistoricoFim !== 'string'
    || typeof payload.proximoMes !== 'string'
    || !resumo
    || !['urgentes', 'atencao', 'seguros', 'dadosInsuficientes'].every((campo) => Number.isInteger(resumo[campo]))
    || !Array.isArray(payload.itens)
    || payload.itens.some((item) => !item
      || !Number.isInteger(item.ingredienteId)
      || typeof item.ingredienteNome !== 'string'
      || typeof item.unidadeCompra !== 'string'
      || !Number.isInteger(item.estoqueAtualG)
      || !FAIXAS.has(item.faixa)
      || (item.faixa !== FaixaPrevisao.DADOS_INSUFICIENTES
        && (![item.estoqueProjetadoInicioMesG, item.demandaMediaMensalG, item.coberturaPct,
          item.quantidadeSugeridaG, item.quantidadeSugeridaCompra].every(Number.isFinite)
          || typeof item.unidadeExibicao !== 'string')))) {
    throw new Error('A API retornou um formato inesperado para a previsão de compras.');
  }
  return payload;
}

export function filtrarItens(itens, filtro) {
  if (!filtro || filtro === 'TODOS') return itens;
  return itens.filter((item) => item.faixa === filtro);
}

export function formatarGramas(valor) {
  if (!Number.isFinite(valor)) return '—';
  if (Math.abs(valor) >= 1000) {
    return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(valor / 1000)} kg`;
  }
  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 }).format(valor)} g`;
}

export function formatarCompra(valor, unidade) {
  if (!Number.isFinite(valor) || !unidade) return '—';
  const casas = unidade === 'un' ? 1 : 2;
  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: casas }).format(valor)} ${unidade}`;
}

export function aparenciaFaixa(faixa) {
  if (faixa === FaixaPrevisao.URGENTE) return { rotulo: 'Urgente', cor: '#ff5252', icone: 'alert-circle' };
  if (faixa === FaixaPrevisao.ATENCAO) return { rotulo: 'Atenção', cor: '#ffb74d', icone: 'warning' };
  if (faixa === FaixaPrevisao.SEGURO) return { rotulo: 'Seguro', cor: '#00e676', icone: 'shield-checkmark' };
  return { rotulo: 'Dados insuficientes', cor: '#9a9ab3', icone: 'help-circle' };
}
