export function avisosSugestao(item) {
  if (Array.isArray(item.avisos)) return item.avisos;
  return item.observacao ? [{ campo: 'GERAL', codigo: 'LEGADO', mensagem: item.observacao }] : [];
}

export function hojeLocal() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function dataValida(texto) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(texto)) return false;
  const data = new Date(`${texto}T12:00:00Z`);
  return !Number.isNaN(data.getTime()) && data.toISOString().slice(0, 10) === texto;
}
export function validarAtivacao(form, prazo, hoje = hojeLocal()) {
  const desconto = String(form.descontoPct).trim();
  if (!/^\d{1,2}$/.test(desconto) || Number(desconto) < 1 || Number(desconto) > 99) throw new Error('Informe um desconto inteiro entre 1 e 99%.');
  if (!dataValida(form.dataInicio) || !dataValida(form.dataFim)) throw new Error('Informe datas válidas no formato AAAA-MM-DD.');
  if (form.dataInicio < hoje || form.dataFim < form.dataInicio || (prazo && form.dataFim > prazo)) throw new Error('O início deve ser hoje ou depois; o fim deve estar entre o início e o prazo do lote.');
  return { descontoPct: Number(desconto), dataInicio: form.dataInicio, dataFim: form.dataFim };
}
export function moeda(valor) {
  const numero = Number(valor);
  return valor == null || !Number.isFinite(numero) ? 'Não informado' : `R$ ${numero.toFixed(2).replace('.', ',')}`;
}
export function erroPromocao(error) {
  if (error.response?.status === 401) return 'Sua sessão expirou. Entre novamente.';
  if ([404, 409].includes(error.response?.status)) return 'A promoção ou o lote mudou no servidor. Atualize a lista antes de tentar novamente.';
  if (error.response?.status === 400) return error.response?.data?.erro || 'Confira o desconto e as datas informadas.';
  if (error.code === 'ECONNABORTED') return 'A API demorou para responder. Atualize a lista para conferir se a ação foi registrada.';
  return error.response?.data?.erro || 'Não foi possível acessar as promoções. Verifique a conexão e tente novamente.';
}
export async function carregarPromocoes(api) {
  const [sugestoes, campanhas, lotes, resumo] = await Promise.all([api.getSugestoes(), api.getCampanhas(), api.getPendentes(), api.getResumo()]);
  if (![sugestoes, campanhas, lotes].every(Array.isArray) || !resumo || !Number.isInteger(resumo.emAndamento) || resumo.prejuizoEvitado == null || !Number.isFinite(Number(resumo.prejuizoEvitado))) throw new Error('Resposta inválida da API.');
  const persistidas = campanhas.filter((item) => item.status === 'SUGERIDA');
  const ocupados = new Set(campanhas.filter((item) => item.status !== 'RECUSADA').map((item) => item.loteId));
  // pega prato mais vendido
  const recomendacoes = new Map();
  for (const item of sugestoes.filter((candidato) => !ocupados.has(candidato.loteId) && Number(candidato.vendas30Dias) > 0)) {
    const atual = recomendacoes.get(item.loteId);
    if (!atual || Number(item.vendas30Dias || 0) > Number(atual.vendas30Dias || 0)
      || (Number(item.vendas30Dias || 0) === Number(atual.vendas30Dias || 0) && item.pratoId < atual.pratoId)) {
      recomendacoes.set(item.loteId, item);
    }
  }
  const candidatos = [...recomendacoes.values()];
  return {
    pendentes: [
      ...persistidas.map((item) => ({ ...lotes.find((lote) => lote.loteId === item.loteId), ...item, chave: `p-${item.id}` })),
      ...candidatos.map((item) => ({ ...item, chave: `s-${item.loteId}-${item.pratoId}` })),
      ...lotes.filter((lote) => !ocupados.has(lote.loteId) && !candidatos.some((item) => item.loteId === lote.loteId)).map((item) => ({ ...item, semCandidato: true, chave: `l-${item.loteId}` })),
    ],
    andamento: campanhas.filter((item) => item.status === 'EM_ANDAMENTO'), resumo,
  };
}
export async function executarPromocao(api, item, acao, dados) {
  if (acao === 'SUCESSO' || acao === 'PERDA') return api.confirmar(item.id, acao === 'SUCESSO');
  if (acao === 'DESCARTAR') return api.descartar(item.loteId);
  const campanha = item.id ? item : await api.criar(item.loteId, item.pratoId);
  return acao === 'ATIVAR' ? api.ativar(campanha.id, dados) : api.recusar(campanha.id);
}
