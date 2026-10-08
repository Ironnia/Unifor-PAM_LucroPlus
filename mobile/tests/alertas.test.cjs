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

const view = carregarModulo('src/screens/alertasViewModel.js');
const alertaCritico = { id: 5, loteId: 12, criticidade: 'CRITICO', diasParaPrazoLimite: 7 };
const alertaAtencao = { id: 6, loteId: 13, criticidade: 'ATENCAO', diasParaPrazoLimite: 8 };

test('lista vazia é um estado válido; resposta malformada é erro', () => {
  assert.deepEqual(view.validarListaAlertas([]), []);
  assert.throws(() => view.validarListaAlertas({ alertas: [] }), /formato inesperado/);
  assert.throws(() => view.validarListaAlertas([{ id: 1, criticidade: 'SAUDAVEL' }]), /formato inesperado/);
});

test('filtros e contagens usam a criticidade 14/7 entregue pelo servidor', () => {
  const alertas = view.validarListaAlertas([alertaCritico, alertaAtencao]);
  assert.deepEqual(view.contarAlertas(alertas), { TODOS: 2, CRITICO: 1, ATENCAO: 1 });
  assert.deepEqual(view.filtrarAlertas(alertas, 'CRITICO'), [alertaCritico]);
  assert.deepEqual(view.filtrarAlertas(alertas, 'ATENCAO'), [alertaAtencao]);
  const depoisDaAcao = alertas.filter((item) => item.id !== 5);
  assert.deepEqual(view.contarAlertas(depoisDaAcao), { TODOS: 1, CRITICO: 0, ATENCAO: 1 });
});

test('prazo e quantidade são apresentados sem converter data por fuso horário', () => {
  assert.equal(view.formatarData('2026-10-01'), '01/10/2026');
  assert.equal(view.formatarQuantidade(1500), '1,50 kg');
  assert.equal(view.formatarQuantidade(300), '300 g');
  assert.equal(view.textoPrazo(0), 'Prazo hoje');
  assert.equal(view.textoPrazo(1), 'Prazo amanhã');
  assert.equal(view.textoPrazo(8), 'Faltam 8 dias');
});

test('prévia ausente não inventa prato ou melhor dia', () => {
  assert.match(view.textoPrevia(null), /indisponível/);
  assert.match(view.textoPrevia({ pratoNome: 'Prato A' }), /Dia sugerido indisponível/);
  assert.match(view.textoPrevia({ pratoNome: 'Prato A', melhorDia: 'sexta-feira' }), /sexta-feira/);
});

test('cliente usa ID do alerta nas duas ações e consulta a fila persistida com Bearer', async () => {
  const chamadas = [];
  let requestInterceptor;
  const client = {
    interceptors: {
      request: { use: (handler) => { requestInterceptor = handler; } },
      response: { use: () => {} },
    },
    get: async (url) => {
      chamadas.push(requestInterceptor({ url, headers: {} }));
      return { data: url === '/alertas/vencimento' ? [alertaCritico] : [{ alertaId: 5, loteId: 12 }] };
    },
    patch: async (url) => {
      chamadas.push(requestInterceptor({ url, headers: {} }));
      return { data: { mensagem: 'ok' } };
    },
  };
  const api = carregarModulo('src/services/api.js', {
    axios: { create: () => client },
    '../config/environment': { API_BASE_URL: 'http://localhost:8080' },
    './sessionStorage': { sessionStorage: {} },
  });
  api.setAuthToken('jwt-de-teste');

  assert.deepEqual(await api.alertasApi.getAtivos(), [alertaCritico]);
  await api.alertasApi.salvarLote(5);
  await api.alertasApi.marcarCiente(6);
  assert.deepEqual(await api.promocoesApi.getPendentes(), [{ alertaId: 5, loteId: 12 }]);
  assert.deepEqual(chamadas.map((item) => item.url), [
    '/alertas/vencimento', '/alertas/5/salvar-lote', '/alertas/6/ciente', '/promocoes/pendentes',
  ]);
  assert.ok(chamadas.every((item) => item.headers.Authorization === 'Bearer jwt-de-teste'));
});
