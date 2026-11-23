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

const view = carregarModulo('src/screens/bcgViewModel.js');

const pratos = [
  { produtoId: 1, nome: 'Estrela A', preco: 30, margemPct: 60, vendas30Dias: 40, lucro30Dias: 720, categoriaBcg: 'ESTRELA' },
  { produtoId: 2, nome: 'Estrela B', preco: 25, margemPct: 55, vendas30Dias: 35, lucro30Dias: 481.25, categoriaBcg: 'ESTRELA' },
  { produtoId: 3, nome: 'Burro', preco: 12, margemPct: 18, vendas30Dias: 50, lucro30Dias: 108, categoriaBcg: 'BURRO_DE_CARGA' },
  { produtoId: 4, nome: 'Quebra', preco: 48, margemPct: 68, vendas30Dias: 8, lucro30Dias: 261.12, categoriaBcg: 'QUEBRA_CABECA' },
  { produtoId: 5, nome: 'Cão', preco: 20, margemPct: 15, vendas30Dias: 5, lucro30Dias: 15, categoriaBcg: 'CAO' },
];

const resposta = { mediaVolume: 27.6, mediaMargemPct: 43.2, totalPratos: 5, pratos };

test('valida o contrato atual da API e rejeita categorias ou totais incorretos', () => {
  assert.deepEqual(view.validarBcg(resposta), resposta);
  assert.throws(() => view.validarBcg({ ...resposta, totalPratos: 4 }), /formato inesperado/);
  assert.throws(() => view.validarBcg({ ...resposta, pratos: [{ ...pratos[0], categoriaBcg: 'INVENTADA' }] }), /formato inesperado/);
});

test('agrupa os pratos nos quatro quadrantes e calcula contadores fiéis', () => {
  const secoes = view.agruparPorQuadrante(pratos);
  assert.deepEqual(secoes.map((secao) => secao.codigo), ['ESTRELA', 'BURRO_DE_CARGA', 'QUEBRA_CABECA', 'CAO']);
  assert.deepEqual(secoes.map((secao) => secao.quantidade), [2, 1, 1, 1]);
  assert.deepEqual(secoes[0].data.map((prato) => prato.produtoId), [1, 2]);
});

test('mantém os textos de negócio aprovados para cada categoria', () => {
  const secoes = view.agruparPorQuadrante([]);
  assert.match(secoes[0].recomendacao, /Manter visibilidade e alta qualidade/);
  assert.match(secoes[1].recomendacao, /renegociar custos dos insumos/);
  assert.match(secoes[2].recomendacao, /promoções temporárias/);
  assert.match(secoes[3].recomendacao, /remoção ou reformulação/);
});

test('aceita histórico mensal opcional sem inventá-lo quando ausente', () => {
  const comHistorico = {
    ...resposta,
    pratos: [{ ...pratos[0], historicoBcg: [{ mesReferencia: '2026-09', categoria: 'QUEBRA_CABECA' }] }, ...pratos.slice(1)],
  };
  assert.equal(view.validarBcg(comHistorico).pratos[0].historicoBcg[0].categoria, 'QUEBRA_CABECA');
  assert.throws(() => view.validarBcg({
    ...comHistorico,
    pratos: [{ ...pratos[0], historicoBcg: [{ mesReferencia: '2026-09', categoria: 'INVALIDA' }] }, ...pratos.slice(1)],
  }), /formato inesperado/);
});

test('cliente chama GET /bcg com Bearer', async () => {
  let requestInterceptor;
  const chamadas = [];
  const client = {
    interceptors: {
      request: { use: (handler) => { requestInterceptor = handler; } },
      response: { use: () => {} },
    },
    get: async (url) => {
      chamadas.push(requestInterceptor({ url, headers: {} }));
      return { data: resposta };
    },
  };
  const api = carregarModulo('src/services/api.js', {
    axios: { create: () => client },
    '../config/environment': { API_BASE_URL: 'http://localhost:8080' },
    './sessionStorage': { sessionStorage: {} },
  });
  api.setAuthToken('jwt-bcg');

  assert.deepEqual(await api.cardapioApi.getBcg(), resposta);
  assert.equal(chamadas[0].url, '/bcg');
  assert.equal(chamadas[0].headers.Authorization, 'Bearer jwt-bcg');
});

test('tela usa SectionList, scroll suave, pull-to-refresh e modal de evolução', () => {
  const screen = fs.readFileSync(path.join(__dirname, '..', 'src/screens/BcgScreen.js'), 'utf8');
  const modal = fs.readFileSync(path.join(__dirname, '..', 'src/components/BcgEvolutionModal.js'), 'utf8');
  assert.match(screen, /<SectionList/);
  assert.match(screen, /scrollToLocation/);
  assert.match(screen, /animated: true/);
  assert.match(screen, /<RefreshControl/);
  assert.match(screen, /<BcgEvolutionModal/);
  assert.doesNotMatch(modal, /Categoria atual · neste mês/);
  assert.doesNotMatch(modal, /MTD/);
  assert.match(modal, /desempenho6Meses/);
  assert.match(modal, /Histórico mensal ainda indisponível/);
});
