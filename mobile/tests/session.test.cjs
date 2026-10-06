const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const babel = require('@babel/core');

function loadApi({ token = null, write = async () => {}, clear = async () => {}, get = async () => ({ data: {} }), post = async () => ({ data: { token: 'jwt-de-teste' } }) } = {}) {
  const requests = [];
  const requestConfigs = [];
  let requestInterceptor;
  let responseFailure;
  const send = async (method, url, data, options = {}) => {
    const config = requestInterceptor({ url, headers: {}, ...options });
    requests.push(url);
    requestConfigs.push(config);
    try {
      return await (method === 'post' ? post(url, data) : get(url));
    } catch (error) {
      error.config = config;
      return responseFailure(error);
    }
  };
  const client = {
    defaults: { headers: { common: {} } },
    interceptors: {
      request: { use: (handler) => { requestInterceptor = handler; } },
      response: { use: (_, handler) => { responseFailure = handler; } },
    },
    post: (url, data, options) => send('post', url, data, options),
    get: (url, options) => send('get', url, null, options),
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
  return { ...module.exports, client, requests, requestConfigs };
}

test('login libera o token somente após gravá-lo', async () => {
  let finishWrite;
  const api = loadApi({ write: () => new Promise((resolve) => { finishWrite = resolve; }) });
  const login = api.authApi.login('teste@exemplo.com', 'senha');
  await new Promise(setImmediate);
  assert.equal(api.getAuthToken(), null);
  finishWrite();
  await login;
  assert.equal(api.getAuthToken(), 'jwt-de-teste');
  await api.authApi.getMe();
  assert.equal(api.requestConfigs[0].headers.Authorization, undefined);
  assert.equal(api.requestConfigs[1].headers.Authorization, 'Bearer jwt-de-teste');
});

test('restauração valida o token salvo em /auth/me', async () => {
  const api = loadApi({ token: 'jwt-salvo' });
  assert.equal(await api.authApi.restoreSession(), true);
  assert.deepEqual(api.requests, ['/auth/me']);
  assert.equal(api.requestConfigs[0].headers.Authorization, 'Bearer jwt-salvo');
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

test('401 em /lotes remove sessão e avisa a navegação uma única vez', async () => {
  let clearCount = 0;
  const messages = [];
  const api = loadApi({
    clear: async () => { clearCount += 1; },
    get: async () => { throw { response: { status: 401 } }; },
  });
  api.subscribeToUnauthorized((message) => messages.push(message));
  api.setAuthToken('jwt-vencido');

  await assert.rejects(api.lotesApi.getLotes());
  await assert.rejects(api.authApi.getMe());

  assert.equal(api.requestConfigs[0].headers.Authorization, 'Bearer jwt-vencido');
  assert.equal(api.requestConfigs[1].headers.Authorization, undefined);
  assert.equal(api.getAuthToken(), null);
  assert.equal(clearCount, 1);
  assert.deepEqual(messages, ['Sua sessão expirou. Entre novamente.']);
});

test('401 no login público mantém a sessão e exibe o erro normal de credenciais', async () => {
  let cleared = false;
  const messages = [];
  const api = loadApi({
    clear: async () => { cleared = true; },
    post: async () => { throw { response: { status: 401, data: { erro: 'Credenciais inválidas' } } }; },
  });
  api.setAuthToken('jwt-anterior');
  api.subscribeToUnauthorized((message) => messages.push(message));

  await assert.rejects(api.authApi.login('teste@exemplo.com', 'errada'));

  assert.equal(api.requestConfigs[0].headers.Authorization, undefined);
  assert.equal(api.getAuthToken(), 'jwt-anterior');
  assert.equal(cleared, false);
  assert.deepEqual(messages, []);
});

test('401 atrasado de token antigo não remove a sessão nova', async () => {
  let rejectOldRequest;
  let cleared = false;
  const messages = [];
  const api = loadApi({
    clear: async () => { cleared = true; },
    get: () => new Promise((_, reject) => { rejectOldRequest = reject; }),
  });
  api.subscribeToUnauthorized((message) => messages.push(message));
  api.setAuthToken('jwt-antigo');
  const request = api.lotesApi.getLotes();
  api.setAuthToken('jwt-novo');
  rejectOldRequest({ response: { status: 401 } });

  await assert.rejects(request);
  assert.equal(api.getAuthToken(), 'jwt-novo');
  assert.equal(cleared, false);
  assert.deepEqual(messages, []);
});

test('falha ao apagar o token avisa que a limpeza persistente não foi concluída', async () => {
  const messages = [];
  const api = loadApi({
    clear: async () => { throw new Error('storage indisponível'); },
    get: async () => { throw { response: { status: 401 } }; },
  });
  api.subscribeToUnauthorized((message) => messages.push(message));
  api.setAuthToken('jwt-vencido');

  await assert.rejects(api.lotesApi.getLotes());
  assert.equal(api.getAuthToken(), null);
  assert.match(messages[0], /não foi possível remover o acesso salvo/);
});

test('duas respostas 401 simultâneas não duplicam a limpeza nem o aviso', async () => {
  let clearCount = 0;
  const messages = [];
  const api = loadApi({
    clear: async () => { clearCount += 1; },
    get: async () => { throw { response: { status: 401 } }; },
  });
  api.subscribeToUnauthorized((message) => messages.push(message));
  api.setAuthToken('jwt-vencido');

  await Promise.allSettled([api.lotesApi.getLotes(), api.authApi.getMe()]);
  assert.equal(clearCount, 1);
  assert.equal(messages.length, 1);
});

test('erro 500 preserva a sessão autenticada', async () => {
  let cleared = false;
  const api = loadApi({
    clear: async () => { cleared = true; },
    get: async () => { throw { response: { status: 500 } }; },
  });
  api.setAuthToken('jwt-valido');

  await assert.rejects(api.lotesApi.getLotes());
  assert.equal(api.getAuthToken(), 'jwt-valido');
  assert.equal(cleared, false);
});
