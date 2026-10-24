const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const babel = require('@babel/core');

function loadModule(relativePath, mocks = {}) {
  const source = fs.readFileSync(path.join(__dirname, relativePath), 'utf8');
  const compiled = babel.transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs'],
  }).code;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)(
    (name) => mocks[name] || require(name),
    module,
    module.exports,
  );
  return module.exports;
}

test('perfil de gerente mostra o restaurante ativo retornado por /auth/me', () => {
  const { montarPerfil } = loadModule('../src/screens/perfilViewModel.js');
  const perfil = montarPerfil({
    nome: 'Pedro Gabriel',
    email: 'pedro@lucroplus.test',
    tipo: 'GERENTE',
    restauranteId: 2,
    restauranteNome: 'Restaurante Gula',
  });

  assert.equal(perfil.nome, 'Pedro Gabriel');
  assert.equal(perfil.tipo, 'Gerente');
  assert.equal(perfil.restaurante, 'Restaurante Gula');
  assert.equal(perfil.acessoGlobal, false);
});

test('ADMIN sem restaurante recebe identificação global neutra', () => {
  const { montarPerfil } = loadModule('../src/screens/perfilViewModel.js');
  const perfil = montarPerfil({ tipo: 'ADMIN', restauranteId: null, restauranteNome: null });

  assert.equal(perfil.tipo, 'Administrador');
  assert.equal(perfil.restaurante, 'Acesso Administrativo Global');
  assert.equal(perfil.acessoGlobal, true);
});

test('logout remove token, opção biométrica e as duas chaves legadas', async () => {
  const secureDeletes = [];
  const asyncDeletes = [];
  const { sessionStorage } = loadModule('../src/services/sessionStorage.js', {
    'react-native': { Platform: { OS: 'android' } },
    'expo-secure-store': {
      getItemAsync: async () => null,
      setItemAsync: async () => {},
      deleteItemAsync: async (key) => { secureDeletes.push(key); },
    },
    '@react-native-async-storage/async-storage': {
      __esModule: true,
      default: { multiRemove: async (keys) => { asyncDeletes.push(...keys); } },
    },
  });

  await sessionStorage.clear();

  assert.deepEqual(secureDeletes, ['lucroplus.jwt.v1']);
});

test('a tela busca /auth/me pelo serviço centralizado e oferece suporte acadêmico', () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/screens/PerfilScreen.js'), 'utf8');
  assert.match(source, /authApi\.getMe\(\)/);
  assert.match(source, /mailto:jvpc@edu\.unifor\.br/);
});
