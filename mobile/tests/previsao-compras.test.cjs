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

const view = carregarModulo('src/screens/previsaoComprasViewModel.js');
const itemValido = {
  ingredienteId: 1,
  ingredienteNome: 'Tomate',
  unidadeCompra: 'kg',
  estoqueAtualG: 4500,
  estoqueProjetadoInicioMesG: 3000,
  demandaMediaMensalG: 8000,
  coberturaPct: 37.5,
  faixa: 'URGENTE',
  quantidadeSugeridaG: 5000,
  quantidadeSugeridaCompra: 5,
  unidadeExibicao: 'kg',
  motivoDadosInsuficientes: null,
};
const payload = {
  periodoHistoricoInicio: '2026-07-01',
  periodoHistoricoFim: '2026-09-30',
  proximoMes: '2026-11',
  resumo: { urgentes: 1, atencao: 0, seguros: 0, dadosInsuficientes: 1 },
  itens: [itemValido, {
    ...itemValido,
    ingredienteId: 2,
    ingredienteNome: 'Manjericão',
    faixa: 'DADOS_INSUFICIENTES',
    estoqueProjetadoInicioMesG: null,
    demandaMediaMensalG: null,
    coberturaPct: null,
    quantidadeSugeridaG: null,
    quantidadeSugeridaCompra: null,
    unidadeExibicao: null,
    motivoDadosInsuficientes: 'Sem vendas suficientes ou ficha técnica no período',
  }],
  produtosSemFichaTecnica: 0,
};

test('valida o payload e rejeita campos numéricos inválidos', () => {
  assert.deepEqual(view.validarPrevisaoCompras(payload), payload);
  assert.throws(() => view.validarPrevisaoCompras({ ...payload, resumo: { urgentes: '1' } }), /formato inesperado/);
  assert.throws(() => view.validarPrevisaoCompras({ ...payload, itens: [{ ...itemValido, coberturaPct: '37.5' }] }), /formato inesperado/);
});

test('filtra as quatro faixas sem esconder a lista completa', () => {
  assert.equal(view.filtrarItens(payload.itens, 'TODOS').length, 2);
  assert.deepEqual(view.filtrarItens(payload.itens, 'URGENTE').map((item) => item.ingredienteId), [1]);
  assert.deepEqual(view.filtrarItens(payload.itens, 'DADOS_INSUFICIENTES').map((item) => item.ingredienteId), [2]);
});

test('formata pesos, unidade de compra e aparências do semáforo', () => {
  assert.match(view.formatarGramas(4500), /4,5 kg/);
  assert.equal(view.formatarGramas(250), '250 g');
  assert.equal(view.formatarCompra(2.5, 'kg'), '2,5 kg');
  assert.equal(view.aparenciaFaixa('URGENTE').rotulo, 'Urgente');
  assert.equal(view.aparenciaFaixa('ATENCAO').rotulo, 'Atenção');
  assert.equal(view.aparenciaFaixa('SEGURO').rotulo, 'Seguro');
  assert.equal(view.aparenciaFaixa('DADOS_INSUFICIENTES').rotulo, 'Dados insuficientes');
});

test('cliente chama a rota de previsão de compras', async () => {
  const chamadas = [];
  const client = {
    get: async (url) => {
      chamadas.push(url);
      return { data: payload };
    },
  };
  const api = carregarModulo('src/services/estoqueApi.js', { './api': client });
  assert.deepEqual(await api.estoqueApi.getPrevisaoCompras(), payload);
  assert.equal(chamadas[0], '/estoque/previsao-compras');
});

test('tela integra abas, resumo, barra, sugestão e pull-to-refresh', () => {
  const estoque = fs.readFileSync(path.join(__dirname, '..', 'src/screens/EstoqueScreen.js'), 'utf8');
  const previsao = fs.readFileSync(path.join(__dirname, '..', 'src/screens/PrevisaoComprasSubScreen.js'), 'utf8');
  assert.match(estoque, /Estoque Atual/);
  assert.match(estoque, /Previsão de Compras/);
  assert.match(estoque, /<PrevisaoComprasSubScreen/);
  assert.match(previsao, /Resumo de compras/);
  assert.doesNotMatch(previsao, /Como calculamos\?/);
  assert.match(previsao, /Demanda prevista/);
  assert.match(previsao, /Sugestão de compra/);
  assert.match(previsao, /<RefreshControl/);
  assert.doesNotMatch(previsao, /<Modal/);
});
