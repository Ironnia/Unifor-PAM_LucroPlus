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

const view = carregarModulo('src/screens/rankingViewModel.js');
const item = {
  posicao: 1, produtoId: 10, nome: 'Prato da casa', preco: 32,
  margemPct: 65, vendasUnidades: 320, lucroBrutoTotal: 6656,
  variacaoPosicao: 2, categoriaBcg: null,
};

test('formata lucro e preço em reais e percentual em pt-BR', () => {
  assert.match(view.formatarMoeda(6656), /R\$\s*6\.656,00/);
  assert.match(view.formatarMoeda(32), /R\$\s*32,00/);
  assert.equal(view.formatarPercentual(65.5), '65,5%');
});

test('variação positiva sobe, negativa cai, zero mantém e nula fica neutra', () => {
  assert.deepEqual(view.obterVariacao(2), { simbolo: '▲', texto: 'Subiu 2', cor: '#4caf50' });
  assert.deepEqual(view.obterVariacao(-1), { simbolo: '▼', texto: 'Caiu 1', cor: '#f44336' });
  assert.deepEqual(view.obterVariacao(0), { simbolo: '—', texto: 'Manteve', cor: '#888888' });
  assert.deepEqual(view.obterVariacao(null), { simbolo: '—', texto: 'Sem comparativo MTD', cor: '#888888' });
});

test('pódio diferencia as três primeiras posições e margem recebe badge', () => {
  assert.deepEqual(view.obterPodio(1), { medalha: '🥇', cor: '#ffd700', rotulo: '1º lugar' });
  assert.deepEqual(view.obterPodio(2), { medalha: '🥈', cor: '#c0c0c0', rotulo: '2º lugar' });
  assert.deepEqual(view.obterPodio(3), { medalha: '🥉', cor: '#cd7f32', rotulo: '3º lugar' });
  assert.equal(view.obterPodio(4), null);
  assert.equal(view.obterSaudeMargem(50).rotulo, 'Alta margem');
  assert.equal(view.obterSaudeMargem(35).rotulo, 'Margem moderada');
  assert.equal(view.obterSaudeMargem(10).rotulo, 'Margem baixa');
});

test('valida o contrato do ranking sem aceitar respostas incompletas', () => {
  assert.deepEqual(view.validarRanking([item]), [item]);
  assert.deepEqual(view.validarRanking([]), []);
  assert.throws(() => view.validarRanking({ ranking: [item] }), /formato inesperado/);
  assert.throws(() => view.validarRanking([{ ...item, lucroBrutoTotal: '6656' }]), /formato inesperado/);
});

test('cliente chama GET /cardapio/ranking com Bearer', async () => {
  let requestInterceptor;
  const chamadas = [];
  const client = {
    interceptors: {
      request: { use: (handler) => { requestInterceptor = handler; } },
      response: { use: () => {} },
    },
    get: async (url) => {
      chamadas.push(requestInterceptor({ url, headers: {} }));
      return { data: [item] };
    },
  };
  const api = carregarModulo('src/services/api.js', {
    axios: { create: () => client },
    '../config/environment': { API_BASE_URL: 'http://localhost:8080' },
    './sessionStorage': { sessionStorage: {} },
  });
  api.setAuthToken('jwt-ranking');

  assert.deepEqual(await api.cardapioApi.getRanking(), [item]);
  assert.equal(chamadas[0].url, '/cardapio/ranking');
  assert.equal(chamadas[0].headers.Authorization, 'Bearer jwt-ranking');
});

test('tela usa FlatList, pull-to-refresh, abas, modal compartilhado e navegação Cardápio', () => {
  const screen = fs.readFileSync(path.join(__dirname, '..', 'src/screens/RankingScreen.js'), 'utf8');
  const navigator = fs.readFileSync(path.join(__dirname, '..', 'src/navigation/AppNavigator.js'), 'utf8');
  assert.match(screen, /<FlatList/);
  assert.match(screen, /<RefreshControl/);
  assert.match(screen, /<BcgScreen/);
  assert.match(screen, /<BcgEvolutionModal/);
  assert.match(screen, /formatarMoeda\(item\.lucroBrutoTotal\)/);
  assert.match(navigator, /name="Cardápio" component=\{RankingScreen\}/);
});
