const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const babel = require('@babel/core');

function load(file, mocks) {
  const code = babel.transformSync(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), {
    babelrc: false, configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs', '@babel/plugin-transform-react-jsx'],
  }).code;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', code)((name) => mocks[name], module, module.exports);
  return module.exports;
}
function nodes(tree) {
  if (!tree || typeof tree !== 'object') return [];
  if (Array.isArray(tree)) return tree.flatMap(nodes);
  return [tree, ...nodes(tree.props?.children)];
}

for (const acao of ['salvarLote', 'marcarCiente']) {
  for (const falhaAcao of [false, true]) {
    test(`${acao}: ${falhaAcao ? 'falha preserva cards' : 'sucesso remove lote mesmo se recarga falhar'}`, async () => {
      let cursor = 0, focus;
      const state = [];
      const React = {
        createElement: (type, props, ...children) => ({ type, props: { ...props, children } }),
        Fragment: 'Fragment', useCallback: (fn) => fn,
        useState: (initial) => {
          const index = cursor++;
          if (!(index in state)) state[index] = initial;
          return [state[index], (value) => { state[index] = value; }];
        },
        useRef: (value) => {
          const index = cursor++;
          if (!(index in state)) state[index] = { current: value };
          return state[index];
        },
      };
      const alerts = [
        { id: 27, loteId: 19, tipo: 'VENCIMENTO', criticidade: 'CRITICO' },
        { id: 28, loteId: 19, tipo: 'VENCIMENTO', criticidade: 'CRITICO' },
        { id: 29, loteId: 20, tipo: 'VENCIMENTO', criticidade: 'ATENCAO' },
      ];
      let gets = 0;
      const calls = [], badges = [];
      const api = {
        getAtivos: async () => { if (gets++ === 0) return alerts; throw new Error('Rede indisponível'); },
        [acao]: async (id) => { calls.push(id); if (falhaAcao) throw new Error('Falha no PATCH'); },
      };
      const native = Object.fromEntries(['ActivityIndicator', 'FlatList', 'Modal', 'RefreshControl', 'Text', 'TouchableOpacity', 'View'].map((key) => [key, key]));
      native.StyleSheet = { create: (x) => x };
      const Screen = load('src/screens/AlertasScreen.js', {
        react: React, 'react-native': native,
        '@react-navigation/native': { useFocusEffect: (fn) => { focus = fn; } },
        '@expo/vector-icons': { Ionicons: 'Ionicons' }, './PromocoesScreen': {},
        '../services/api': { alertasApi: api },
        '../services/nativeService': {
          impactoLeve: async () => {}, impactoSucesso: async () => {},
          notificarLoteCritico: async () => true,
        },
        './alertasViewModel': load('src/screens/alertasViewModel.js', {}),
      }).default;
      const render = () => { cursor = 0; return Screen({ onCountChange: (n) => badges.push(n) }); };
      render(); focus(); await new Promise(setImmediate);
      let tree = render();
      const list = nodes(tree).find((x) => x.type === 'FlatList');
      const card = list.props.renderItem({ item: alerts[0] });
      const label = acao === 'salvarLote' ? 'Salvar lote do alerta 27' : 'Marcar alerta 27 como ciente';
      nodes(card).find((x) => x.props.accessibilityLabel === label).props.onPress();
      tree = render();
      const button = nodes(tree).find((x) => x.type === 'TouchableOpacity'
        && nodes(x).some((child) => child.type === 'Text' && child.props.children.includes('Confirmar')));
      await button.props.onPress();
      tree = render();
      assert.deepEqual(calls, [27]);
      assert.deepEqual(nodes(tree).find((x) => x.type === 'FlatList').props.data.map((x) => x.id), falhaAcao ? [27, 28, 29] : [29]);
      assert.equal(badges.at(-1), falhaAcao ? 3 : 1);
      assert.equal(gets, falhaAcao ? 1 : 2);
    });
  }
}
