export const FILTROS_ALERTA = ['TODOS', 'CRITICO', 'ATENCAO'];

export function validarListaAlertas(dados) {
  if (!Array.isArray(dados) || dados.some((item) =>
    !item || !Number.isInteger(item.id) || !['CRITICO', 'ATENCAO'].includes(item.criticidade))) {
    throw new Error('A API retornou um formato inesperado para os alertas.');
  }
  return dados;
}

export function filtrarAlertas(alertas, filtro) {
  return filtro === 'TODOS' ? alertas : alertas.filter((alerta) => alerta.criticidade === filtro);
}

export function contarAlertas(alertas) {
  return {
    TODOS: alertas.length,
    CRITICO: alertas.filter((alerta) => alerta.criticidade === 'CRITICO').length,
    ATENCAO: alertas.filter((alerta) => alerta.criticidade === 'ATENCAO').length,
  };
}

export function formatarData(data) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(data || '');
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]}` : 'Não informado';
}

export function formatarQuantidade(gramas) {
  if (gramas == null || gramas === '') return 'Não informada';
  const quantidade = Number(gramas);
  if (!Number.isFinite(quantidade)) return 'Não informada';
  if (quantidade >= 1000) return `${(quantidade / 1000).toFixed(2).replace('.', ',')} kg`;
  return `${quantidade.toFixed(0)} g`;
}

export function textoPrazo(dias) {
  if (dias === 0) return 'Prazo hoje';
  if (dias === 1) return 'Prazo amanhã';
  if (Number.isInteger(dias) && dias > 1) return `Faltam ${dias} dias`;
  return 'Prazo não informado';
}

export function textoPrevia(previsao) {
  if (!previsao?.pratoNome) return 'Recomendação de prato e dia indisponível no momento.';
  if (!previsao?.melhorDia) return `Prato sugerido: ${previsao.pratoNome}. Dia sugerido indisponível.`;
  return `Prato sugerido: ${previsao.pratoNome}. Melhor dia: ${previsao.melhorDia}.`;
}
