export const CATEGORIAS_BCG = [
  {
    codigo: 'ESTRELA',
    titulo: 'Estrelas',
    singular: 'Estrela',
    icone: '⭐',
    cor: '#ffd166',
    combinacao: 'Margem alta + volume alto',
    recomendacao: 'Manter visibilidade e alta qualidade.',
  },
  {
    codigo: 'BURRO_DE_CARGA',
    titulo: 'Burros de Carga',
    singular: 'Burro de Carga',
    icone: '🐂',
    cor: '#64b5f6',
    combinacao: 'Margem baixa + volume alto',
    recomendacao: 'Aumentar levemente o preço ou renegociar custos dos insumos.',
  },
  {
    codigo: 'QUEBRA_CABECA',
    titulo: 'Quebra-Cabeças',
    singular: 'Quebra-Cabeça',
    icone: '🧩',
    cor: '#ba8cff',
    combinacao: 'Margem alta + volume baixo',
    recomendacao: 'Destacar no cardápio ou fazer promoções temporárias.',
  },
  {
    codigo: 'CAO',
    titulo: 'Cães',
    singular: 'Cão',
    icone: '🐕',
    cor: '#ef767a',
    combinacao: 'Margem baixa + volume baixo',
    recomendacao: 'Candidatos a remoção ou reformulação de receita.',
  },
];

const categoriasValidas = new Set(CATEGORIAS_BCG.map((categoria) => categoria.codigo));

function historicoValido(historico) {
  return Array.isArray(historico) && historico.every((registro) =>
    registro
    && typeof registro.mesReferencia === 'string'
    && categoriasValidas.has(registro.categoria));
}

export function validarBcg(dados) {
  const respostaValida = dados
    && Number.isFinite(dados.mediaVolume)
    && Number.isFinite(dados.mediaMargemPct)
    && Number.isInteger(dados.totalPratos)
    && Array.isArray(dados.pratos)
    && dados.totalPratos === dados.pratos.length;

  const pratosValidos = respostaValida && dados.pratos.every((item) =>
    item
    && Number.isInteger(item.produtoId)
    && typeof item.nome === 'string'
    && Number.isFinite(item.preco)
    && Number.isFinite(item.margemPct)
    && Number.isInteger(item.vendas30Dias)
    && Number.isFinite(item.lucro30Dias)
    && categoriasValidas.has(item.categoriaBcg)
    && (item.historicoBcg == null || historicoValido(item.historicoBcg)));

  if (!pratosValidos) {
    throw new Error('A API retornou um formato inesperado para a Matriz BCG.');
  }
  return dados;
}

export function agruparPorQuadrante(pratos) {
  return CATEGORIAS_BCG.map((categoria) => {
    const itens = pratos.filter((prato) => prato.categoriaBcg === categoria.codigo);
    // O item vazio mantém uma âncora real na SectionList. Assim, o toque no grid
    const data = itens.length ? itens : [{ produtoId: `vazio-${categoria.codigo}`, vazio: true }];
    return { ...categoria, quantidade: itens.length, data };
  });
}

export function obterCategoria(codigo) {
  return CATEGORIAS_BCG.find((categoria) => categoria.codigo === codigo) || null;
}

export function pluralizarPratos(quantidade) {
  return `${quantidade} ${quantidade === 1 ? 'prato' : 'pratos'}`;
}
