const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const babel = require('@babel/core');

function carregarModulo(arquivo, mocks = {}) {
  const source = fs.readFileSync(path.join(__dirname, '..', arquivo), 'utf8');
  const compiled = babel.transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs'],
  }).code;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)((name) => mocks[name], module, module.exports);
  return module.exports;
}

const view = carregarModulo('src/screens/dashboardViewModel.js');
const payload = {
  faturamentoTotal: 1234.56,
  totalPedidos: 42,
  totalProdutosVendidos: 67,
  ticketMedio: 29.394,
  impactoFinanceiro: { valorSalvo: 850, valorPerdido: 120, impactoLiquido: 730, economiaGeradaPct: 87.6 },
  variacoes: { faturamentoPct: 5.2, totalPedidosPct: -2.1, ticketMedioPct: 0, impactoLiquidoPct: null },
  periodoAtual: { inicio: '2026-10-01', fim: '2026-10-04' },
  periodoComparativo: { inicio: '2026-09-01', fim: '2026-09-04' },
  ultimosSeteDias: [{ data: '2026-10-04', totalVendas: 320, quantidadePedidos: 9 }],
};

test('valida todos os campos obrigatórios do DashboardKpisDto', () => {
  assert.deepEqual(view.validarPayloadDashboard(payload), payload);
  assert.throws(() => view.validarPayloadDashboard({ ...payload, ticketMedio: '29,39' }), /formato inesperado/);
  assert.throws(() => view.validarPayloadDashboard({ ...payload, impactoFinanceiro: { valorSalvo: 850 } }), /formato inesperado/);
  assert.throws(() => view.validarPayloadDashboard({ ...payload, ultimosSeteDias: [{ data: '04/10/2026', totalVendas: 320, quantidadePedidos: 9 }] }), /formato inesperado/);
});

test('formata moeda em reais com padrão brasileiro', () => {
  const valor = view.formatarMoeda(1234.56).replace(/\u00a0/g, ' ');
  assert.equal(valor, 'R$ 1.234,56');
});

test('formata variações positiva, negativa, neutra e ausente', () => {
  assert.deepEqual(view.formatarVariacao(5.2), { texto: '▲ +5,2%', cor: '#4caf50' });
  assert.deepEqual(view.formatarVariacao(-2.1), { texto: '▼ -2,1%', cor: '#ef6c75' });
  assert.deepEqual(view.formatarVariacao(0), { texto: '— 0,0%', cor: '#9a9ab3' });
  assert.deepEqual(view.formatarVariacao(null), { texto: '—', cor: '#9a9ab3' });
});

test('formata os sete dias com Hoje na data atual informada', () => {
  const hoje = new Date(2026, 9, 4, 12, 0, 0);
  assert.equal(view.formatarDataGrafico('2026-10-04', hoje), 'Hoje');
  assert.equal(view.formatarDataGrafico('2026-10-03', hoje), 'Sáb');
  assert.equal(view.formatarDataGrafico('2026-10-01', hoje), 'Qui');
  assert.equal(view.formatarDataGrafico('data-invalida', hoje), '—');
});

test('formata a última sincronização e trata ausência ou data inválida', () => {
  const texto = view.formatarUltimaSincronizacao('2026-10-04T09:30:00-03:00');
  assert.match(texto, /^04\/10\/2026 às 09:30$/);
  assert.equal(view.formatarUltimaSincronizacao(null), 'Ainda não realizada');
  assert.equal(view.formatarUltimaSincronizacao('invalida'), 'Data indisponível');
});

test('cliente usa as três rotas autenticadas do Dashboard e timeout próprio na sincronização', async () => {
  let requestInterceptor;
  const chamadas = [];
  const client = {
    interceptors: {
      request: { use: (handler) => { requestInterceptor = handler; } },
      response: { use: () => {} },
    },
    get: async (url) => {
      chamadas.push(requestInterceptor({ method: 'get', url, headers: {} }));
      return { data: url.endsWith('status') ? { ultimaSincronizacao: null } : payload };
    },
    post: async (url, body, config = {}) => {
      chamadas.push(requestInterceptor({ method: 'post', url, headers: {}, ...config }));
      return { data: { sucesso: true, ultimaSincronizacao: '2026-10-04T09:30:00-03:00' } };
    },
  };
  const api = carregarModulo('src/services/api.js', {
    axios: { create: () => client },
    '../config/environment': { API_BASE_URL: 'http://localhost:8080' },
    './sessionStorage': { sessionStorage: {} },
  });
  api.setAuthToken('jwt-dashboard');

  await api.dashboardApi.getKpis();
  await api.dashboardApi.getStatusPdv();
  await api.dashboardApi.sincronizarPdv();

  assert.deepEqual(chamadas.map((item) => item.url), ['/dashboard/kpis', '/importacao/pdv/status', '/importacao/pdv']);
  assert.ok(chamadas.every((item) => item.headers.Authorization === 'Bearer jwt-dashboard'));
  assert.equal(chamadas[2].timeout, 60000);
});

test('tela contém grade, dois gráficos, pull-to-refresh e feedback de sincronização', () => {
  const screen = fs.readFileSync(path.join(__dirname, '..', 'src/screens/HomeScreen.js'), 'utf8');
  assert.match(screen, /<RefreshControl/);
  assert.match(screen, /titulo="Faturamento"/);
  assert.match(screen, /#00e676/);
  assert.match(screen, /Total de pedidos/);
  assert.match(screen, /Ticket médio/);
  assert.match(screen, /<ImpactoCard/);
  assert.match(screen, /Pedidos por dia/);
  assert.match(screen, /Faturamento por dia/);
  assert.match(screen, /Sincronizando\.\.\./);
  assert.match(screen, /Sincronização concluída/);
  assert.match(screen, /await carregar\(\{ preservar: true \}\)/);
});
