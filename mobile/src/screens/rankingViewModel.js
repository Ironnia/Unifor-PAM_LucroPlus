export function validarRanking(dados) {
  if (!Array.isArray(dados) || dados.some((item) =>
    !item
    || !Number.isInteger(item.posicao)
    || !Number.isInteger(item.produtoId)
    || typeof item.nome !== 'string'
    || !Number.isFinite(item.preco)
    || !Number.isFinite(item.margemPct)
    || !Number.isInteger(item.vendasUnidades)
    || !Number.isFinite(item.lucroBrutoTotal)
    || (item.variacaoPosicao != null && !Number.isInteger(item.variacaoPosicao)))) {
    throw new Error('A API retornou um formato inesperado para o ranking.');
  }
  return dados;
}

export function formatarMoeda(valor) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
  }).format(valor);
}

export function formatarPercentual(valor) {
  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(valor)}%`;
}

export function obterSaudeMargem(margemPct) {
  if (margemPct >= 50) return { rotulo: 'Alta margem', cor: '#4caf50' };
  if (margemPct >= 20) return { rotulo: 'Margem moderada', cor: '#ffc107' };
  return { rotulo: 'Margem baixa', cor: '#f44336' };
}

export function obterVariacao(variacaoPosicao) {
  if (variacaoPosicao == null) {
    return { simbolo: '—', texto: 'Sem comparativo MTD', cor: '#888888' };
  }
  if (variacaoPosicao > 0) {
    return { simbolo: '▲', texto: `Subiu ${variacaoPosicao}`, cor: '#4caf50' };
  }
  if (variacaoPosicao < 0) {
    return { simbolo: '▼', texto: `Caiu ${Math.abs(variacaoPosicao)}`, cor: '#f44336' };
  }
  return { simbolo: '—', texto: 'Manteve', cor: '#888888' };
}

export function obterPodio(posicao) {
  if (posicao === 1) return { medalha: '🥇', cor: '#ffd700', rotulo: '1º lugar' };
  if (posicao === 2) return { medalha: '🥈', cor: '#c0c0c0', rotulo: '2º lugar' };
  if (posicao === 3) return { medalha: '🥉', cor: '#cd7f32', rotulo: '3º lugar' };
  return null;
}
