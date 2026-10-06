const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const babel = require('@babel/core');

function loadApi({ token = null, write = async () => {}, clear = async () => {}, get = async () => ({ data: {} }) } = {}) {
  const requests = [];
  const client = {
    defaults: { headers: { common: {} } },
    post: async (url) => {
      requests.push(url);
      return { data: { token: 'jwt-de-teste' } };
    },
    get: async (url) => {
      requests.push(url);
      return get();
    },
  };
  const storage = { read: async () => token, write, clear };
  const source = fs.readFileSync(path.join(__dirname, '../src/services/api.js'), 'utf8');
  const compiled = babel.transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs'],
  }).code;
  const module = { exports: {} };
  const mocks = {
    axios: { create: () => client },
    '../config/environment': { API_BASE_URL: 'http://localhost:8080' },
    './sessionStorage': { sessionStorage: storage },
  };
  new Function('require', 'module', 'exports', compiled)((name) => mocks[name], module, module.exports);
  return { ...module.exports, client, requests };
}

test('login libera o token somente após gravá-lo', async () => {
  let finishWrite;
  const api = loadApi({ write: () => new Promise((resolve) => { finishWrite = resolve; }) });
  const login = api.authApi.login('teste@exemplo.com', 'senha');
  await Promise.resolve();
  assert.equal(api.getAuthToken(), null);
  finishWrite();
  await login;
  assert.equal(api.getAuthToken(), 'jwt-de-teste');
  assert.equal(api.client.defaults.headers.common.Authorization, 'Bearer jwt-de-teste');
});

test('restauração valida o token salvo em /auth/me', async () => {
  const api = loadApi({ token: 'jwt-salvo' });
  assert.equal(await api.authApi.restoreSession(), true);
  assert.deepEqual(api.requests, ['/auth/me']);
  assert.equal(api.client.defaults.headers.common.Authorization, 'Bearer jwt-salvo');
});

test('401 elimina a sessão salva', async () => {
  let cleared = false;
  const api = loadApi({
    token: 'jwt-vencido',
    get: async () => { throw { response: { status: 401 } }; },
    clear: async () => { cleared = true; },
  });
  assert.equal(await api.authApi.restoreSession(), false);
  assert.equal(cleared, true);
  assert.equal(api.getAuthToken(), null);
});

test('falha de rede preserva o token para a próxima abertura', async () => {
  let cleared = false;
  const api = loadApi({
    token: 'jwt-salvo',
    get: async () => { throw new Error('sem rede'); },
    clear: async () => { cleared = true; },
  });
  assert.equal(await api.authApi.restoreSession(), false);
  assert.equal(cleared, false);
  assert.equal(api.getAuthToken(), null);
});

test('logout só termina após remover o token do aparelho', async () => {
  let fail = true;
  const api = loadApi({ clear: async () => { if (fail) throw new Error('falha no storage'); } });
  await api.authApi.login('teste@exemplo.com', 'senha');
  await assert.rejects(api.authApi.logout(), /falha no storage/);
  assert.equal(api.getAuthToken(), 'jwt-de-teste');
  fail = false;
  await api.authApi.logout();
  assert.equal(api.getAuthToken(), null);
});
