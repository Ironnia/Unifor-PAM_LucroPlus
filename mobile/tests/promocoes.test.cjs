const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const babel = require('@babel/core');
function load(file, mocks = {}) {
  const code = babel.transformSync(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    babelrc: false, configFile: false, plugins: ['@babel/plugin-transform-modules-commonjs', '@babel/plugin-transform-react-jsx'],
  }).code;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', code)((name) => mocks[name], module, module.exports);
  return module.exports;
}
const view = load('src/screens/promocoesViewModel.js');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const { InformacoesSugestao } = load('src/screens/PromocoesScreen.js', {
  react: React,
  'react-native': { Text: ({ children }) => React.createElement('p', null, children), StyleSheet: { create: (styles) => styles } },
  '@react-navigation/native': {}, '../services/api': {},
  './alertasViewModel': { formatarData: (data) => data }, './promocoesViewModel': view,
});

test('avisos novos substituem resumo sem duplicar e lista vazia permanece vazia', () => {
  assert.deepEqual(view.avisosSugestao({ observacao: 'Legado' }).map((a) => a.mensagem), ['Legado']);
  assert.deepEqual(view.avisosSugestao({ avisos: [], observacao: 'Legado' }), []);
  assert.deepEqual(view.avisosSugestao({}), []);
});

test('componente renderiza os dois motivos junto dos campos sem repetir o resumo', () => {
  const avisos = [
    { campo: 'MARGEM', codigo: 'SEM_CUSTO_ELEGIVEL', ingredienteId: 5, mensagem: 'Alface sem custo elegível.' },
    { campo: 'MELHOR_DIA', codigo: 'SEM_DIA_COMPATIVEL', mensagem: 'Há vendas, mas nenhum dia cabe no prazo.' },
  ];
  const html = renderToStaticMarkup(React.createElement(InformacoesSugestao, { item: { avisos, observacao: avisos.map((a) => a.mensagem).join(' ') } }));
  assert.match(html, /Melhor dia: Indisponível/);
  assert.match(html, /Lucro estimado: Indisponível/);
  for (const aviso of avisos) assert.equal(html.split(aviso.mensagem).length - 1, 1);
  assert.ok(html.indexOf(avisos[1].mensagem) < html.indexOf('Lucro estimado'));
  assert.ok(html.indexOf(avisos[0].mensagem) > html.indexOf('Lucro estimado'));
});

test('componente preserva margem zero e negativa e mostra observacao de API antiga', () => {
  for (const margemPct of [0, -12.5]) {
    const html = renderToStaticMarkup(React.createElement(InformacoesSugestao, { item: {
      margemPct, melhorDiaSemana: 'Sábado', dataSugerida: '2026-09-26', observacao: 'Mensagem antiga',
    } }));
    assert.ok(html.includes('Lucro estimado: Indisponível'));
    assert.ok(html.includes('Melhor dia: Sábado (2026-09-26)'));
    assert.ok(html.includes('Mensagem antiga'));
  }
});
const form = { descontoPct: '20', dataInicio: '2026-09-26', dataFim: '2026-09-28' };
test('desconto obrigatório e inteiro de 1 a 99, sem bloquear pela margem', () => {
  for (const descontoPct of ['', '0', '100', '-1', '1.5', 'abc']) assert.throws(() => view.validarAtivacao({ ...form, descontoPct }, '2026-09-30', '2026-09-26'), /desconto/);
  for (const descontoPct of ['1', '99']) assert.equal(view.validarAtivacao({ ...form, descontoPct }, '2026-09-30', '2026-09-26').descontoPct, Number(descontoPct));
});
test('datas reais, início não passado, ordem e prazo inclusivo do lote', () => {
  assert.deepEqual(view.validarAtivacao(form, form.dataFim, form.dataInicio), { ...form, descontoPct: 20 });
  for (const mudanca of [{ dataInicio: '2026-09-25' }, { dataFim: '2026-09-25' }, { dataFim: '2026-10-01' }, { dataFim: '2026-02-30' }, { dataFim: '' }, { dataFim: '28/09/2026' }]) assert.throws(() => view.validarAtivacao({ ...form, ...mudanca }, '2026-09-30', '2026-09-26'));
});
function leitura(campanhas = [], sugestoes = [], lotes = []) {
  return { getCampanhas: async () => campanhas, getSugestoes: async () => sugestoes, getPendentes: async () => lotes, getResumo: async () => ({ emAndamento: 1, prejuizoEvitado: '15.18' }) };
}
test('persistidas sobrevivem, lotes sem candidatos aparecem e duplicatas não', async () => {
  const campanhas = [{ id: 1, loteId: 10, status: 'SUGERIDA' }, { id: 2, loteId: 20, status: 'EM_ANDAMENTO', aguardaConfirmacao: false }, { id: 3, loteId: 30, status: 'CONCLUIDA_SUCESSO' }, { id: 4, loteId: 40, status: 'RECUSADA' }];
  const resultado = await view.carregarPromocoes(leitura(campanhas, [{ loteId: 10, pratoId: 1, vendas30Dias: 2 }, { loteId: 40, pratoId: 5, vendas30Dias: 3 }], [{ loteId: 10, prazoLimiteVenda: '2026-09-28' }, { loteId: 50 }]));
  assert.deepEqual(resultado.pendentes.map((p) => p.chave), ['p-1', 's-40-5', 'l-50']);
  assert.equal(resultado.pendentes[0].prazoLimiteVenda, '2026-09-28');
  assert.equal(resultado.pendentes[2].semCandidato, true);
  assert.equal(resultado.andamento[0].aguardaConfirmacao, false);
  assert.equal(resultado.resumo.prejuizoEvitado, '15.18');
});
test('vazio é válido; resposta malformada ou falha parcial não inventa totais', async () => {
  assert.deepEqual((await view.carregarPromocoes(leitura())).pendentes, []);
  await assert.rejects(view.carregarPromocoes(leitura({})), /inválida/);
  await assert.rejects(view.carregarPromocoes({ ...leitura(), getResumo: async () => { throw Error('offline'); } }), /offline/);
});
test('POST precede PATCH; retomada não recria; confirmação envia booleano', async () => {
  const calls = [];
  const api = { criar: async (...args) => { calls.push(['criar', ...args]); return { id: 7 }; }, ativar: async (...args) => calls.push(['ativar', ...args]), recusar: async (...args) => calls.push(['recusar', ...args]), confirmar: async (...args) => calls.push(['confirmar', ...args]) };
  await view.executarPromocao(api, { loteId: 2, pratoId: 3 }, 'ATIVAR', form);
  await view.executarPromocao(api, { id: 7 }, 'RECUSAR');
  await view.executarPromocao(api, { id: 7 }, 'SUCESSO');
  await view.executarPromocao(api, { id: 8 }, 'PERDA');
  assert.deepEqual(calls, [['criar', 2, 3], ['ativar', 7, form], ['recusar', 7], ['confirmar', 7, true], ['confirmar', 8, false]]);
});
test('recomendação única usa mais vendas e desempata pelo menor pratoId', async () => {
  const resultado = await view.carregarPromocoes(leitura([], [
    { loteId: 3, pratoId: 9, vendas30Dias: 8 },
    { loteId: 3, pratoId: 4, vendas30Dias: 8 },
    { loteId: 3, pratoId: 2, vendas30Dias: 7 },
  ], []));
  assert.deepEqual(resultado.pendentes.map(({ loteId, pratoId }) => [loteId, pratoId]), [[3, 4]]);
});
test('zero vendas mantém lote pendente sem recomendação inventada', async () => {
  const resultado = await view.carregarPromocoes(leitura([], [{ loteId: 6, pratoId: 12, vendas30Dias: 0 }], [{ loteId: 6 }]));
  assert.deepEqual(resultado.pendentes.map((item) => item.chave), ['l-6']);
  assert.equal(resultado.pendentes[0].semCandidato, true);
});
test('card mostra preço, lucro bruto e limite consultivo', () => {
  const html = renderToStaticMarkup(React.createElement(InformacoesSugestao, { item: {
    precoVenda: 10, lucroBrutoEstimado: 2, margemPct: 20, limiteDescontoMargemBrutaPct: 19,
  } }));
  assert.match(html, /Preço de venda: R\$ 10,00/);
  assert.match(html, /Lucro estimado: R\$ 2,00 \(20\.0%\)/);
  assert.match(html, /Desconto recomendado: até 19%/);
});
test('descarte sem candidato chama rota própria; recusa encerra sem alternativa', async () => {
  const chamadas = [];
  const api = { descartar: async (...args) => chamadas.push(['descartar', ...args]), recusar: async (...args) => chamadas.push(['recusar', ...args]) };
  await view.executarPromocao(api, { loteId: 15 }, 'DESCARTAR');
  await view.executarPromocao(api, { id: 31 }, 'RECUSAR');
  assert.deepEqual(chamadas, [['descartar', 15], ['recusar', 31]]);
});
test('falha de POST impede PATCH e falha de PATCH permite recuperação', async () => {
  let ativou = false;
  await assert.rejects(view.executarPromocao({ criar: async () => { throw Error('409'); }, ativar: async () => { ativou = true; } }, { loteId: 1, pratoId: 1 }, 'ATIVAR', form), /409/);
  assert.equal(ativou, false);
  await assert.rejects(view.executarPromocao({ criar: async () => ({ id: 1 }), ativar: async () => { throw Error('timeout'); } }, { loteId: 1, pratoId: 1 }, 'ATIVAR', form), /timeout/);
});
test('rotas, verbos, corpos e JWT seguem o contrato', async () => {
  const calls = []; let intercept;
  const client = { interceptors: { request: { use: (fn) => { intercept = fn; } }, response: { use: () => {} } } };
  for (const method of ['get', 'post', 'patch']) client[method] = async (url, data) => { calls.push(intercept({ method, url, data, headers: {} })); return { data: {} }; };
  const api = load('src/services/api.js', { axios: { create: () => client }, '../config/environment': { API_BASE_URL: 'http://localhost' }, './sessionStorage': { sessionStorage: {} } });
  api.setAuthToken('jwt-ficticio');
  await api.promocoesApi.getSugestoes(); await api.promocoesApi.getCampanhas(); await api.promocoesApi.getPendentes(); await api.promocoesApi.getResumo();
  await api.promocoesApi.criar(2, 3); await api.promocoesApi.ativar(7, form); await api.promocoesApi.recusar(8); await api.promocoesApi.descartar(10); await api.promocoesApi.confirmar(9, false);
  assert.deepEqual(calls.map(({ method, url, data }) => [method, url, data]), [['get', '/promocoes/sugestoes', undefined], ['get', '/promocoes', undefined], ['get', '/promocoes/pendentes', undefined], ['get', '/promocoes/resumo', undefined], ['post', '/promocoes', { loteId: 2, pratoId: 3 }], ['patch', '/promocoes/7/ativar', form], ['patch', '/promocoes/8/recusar', undefined], ['patch', '/promocoes/pendentes/10/descartar', undefined], ['patch', '/promocoes/9/confirmar', { loteEscoado: false }]]);
  assert.ok(calls.every((call) => call.headers.Authorization === 'Bearer jwt-ficticio'));
});
test('mensagens distinguem sessão, conflito, validação e timeout', () => {
  assert.match(view.erroPromocao({ response: { status: 401 } }), /sessão/);
  assert.match(view.erroPromocao({ response: { status: 409 } }), /Atualize/);
  assert.match(view.erroPromocao({ response: { status: 400 } }), /desconto/);
  assert.match(view.erroPromocao({ code: 'ECONNABORTED' }), /registrada/);
  assert.equal(view.moeda('15.18'), 'R$ 15,18'); assert.equal(view.moeda(null), 'Não informado');
});
