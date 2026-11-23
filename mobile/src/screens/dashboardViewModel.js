const COR_POSITIVA = '#4caf50';
const COR_NEGATIVA = '#ef6c75';
const COR_NEUTRA = '#9a9ab3';

function numeroFinito(valor) {
  return typeof valor === 'number' && Number.isFinite(valor);
}

function periodoValido(periodo) {
  return periodo
    && typeof periodo.inicio === 'string'
    && typeof periodo.fim === 'string';
}

function serieValida(item) {
  return item
    && /^\d{4}-\d{2}-\d{2}$/.test(item.data)
    && numeroFinito(item.totalVendas)
    && Number.isInteger(item.quantidadePedidos);
}

export function validarPayloadDashboard(dados) {
  const impacto = dados?.impactoFinanceiro;
  const variacoes = dados?.variacoes;
  const valido = dados
    && numeroFinito(dados.faturamentoTotal)
    && Number.isInteger(dados.totalPedidos)
    && Number.isInteger(dados.totalProdutosVendidos)
    && numeroFinito(dados.ticketMedio)
    && impacto
    && numeroFinito(impacto.valorSalvo)
    && numeroFinito(impacto.valorPerdido)
    && numeroFinito(impacto.impactoLiquido)
    && numeroFinito(impacto.economiaGeradaPct)
    && variacoes
    && ['faturamentoPct', 'totalPedidosPct', 'ticketMedioPct', 'impactoLiquidoPct']
      .every((campo) => variacoes[campo] == null || numeroFinito(variacoes[campo]))
    && periodoValido(dados.periodoAtual)
    && (dados.periodoComparativo == null || periodoValido(dados.periodoComparativo))
    && Array.isArray(dados.ultimosSeteDias)
    && dados.ultimosSeteDias.every(serieValida);

  if (!valido) {
    throw new Error('A API retornou um formato inesperado para o Dashboard.');
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

export function formatarVariacao(pct) {
  if (pct == null) return { texto: '—', cor: COR_NEUTRA };
  const percentual = new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(Math.abs(pct));
  if (pct > 0) return { texto: `▲ +${percentual}%`, cor: COR_POSITIVA };
  if (pct < 0) return { texto: `▼ -${percentual}%`, cor: COR_NEGATIVA };
  return { texto: '— 0,0%', cor: COR_NEUTRA };
}

function dataLocal(dataIso) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dataIso);
  if (!partes) return null;
  const data = new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]));
  if (data.getFullYear() !== Number(partes[1])
    || data.getMonth() !== Number(partes[2]) - 1
    || data.getDate() !== Number(partes[3])) return null;
  return data;
}

export function formatarDataGrafico(dataIso, hoje = new Date()) {
  const data = dataLocal(dataIso);
  if (!data) return '—';
  if (data.getFullYear() === hoje.getFullYear()
    && data.getMonth() === hoje.getMonth()
    && data.getDate() === hoje.getDate()) return 'Hoje';
  return ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'][data.getDay()];
}

export function formatarUltimaSincronizacao(isoString) {
  if (!isoString) return 'Ainda não realizada';
  const data = new Date(isoString);
  if (Number.isNaN(data.getTime())) return 'Data indisponível';
  const partes = new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false,
    timeZone: 'America/Fortaleza',
  }).formatToParts(data).reduce((acc, parte) => ({ ...acc, [parte.type]: parte.value }), {});
  return `${partes.day}/${partes.month}/${partes.year} às ${partes.hour}:${partes.minute}`;
}
