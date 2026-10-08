import axios from 'axios';
import { API_BASE_URL } from '../config/environment';
import { sessionStorage } from './sessionStorage';

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

let authToken = null;
let unauthorizedListener = null;
let invalidationPromise = null;

const SESSION_EXPIRED_MESSAGE = 'Sua sessão expirou. Entre novamente.';
const SESSION_CLEAR_FAILED_MESSAGE = 'Sua sessão expirou, mas não foi possível remover o acesso salvo neste aparelho. Tente novamente.';

export const setAuthToken = (token) => {
  authToken = token;
};

export const getAuthToken = () => authToken;

export const subscribeToUnauthorized = (listener) => {
  unauthorizedListener = listener;
  return () => {
    if (unauthorizedListener === listener) unauthorizedListener = null;
  };
};

client.interceptors.request.use((config) => {
  if (config.skipAuth) return config;
  if (authToken) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${authToken}`;
    config.sessionToken = authToken;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const requestToken = error.config?.sessionToken;
    if (error.response?.status === 401 && requestToken && requestToken === authToken) {
      setAuthToken(null);
      const task = sessionStorage.clear()
        .then(
          () => unauthorizedListener?.(SESSION_EXPIRED_MESSAGE),
          () => unauthorizedListener?.(SESSION_CLEAR_FAILED_MESSAGE),
        )
        .finally(() => {
          if (invalidationPromise === task) invalidationPromise = null;
        });
      invalidationPromise = task;
      await task;
    }
    return Promise.reject(error);
  },
);

const saveSession = async (token) => {
  if (!token) {
    throw new Error('O servidor não retornou um token de acesso.');
  }

  try {
    if (invalidationPromise) await invalidationPromise;
    await sessionStorage.write(token);
  } catch (_) {
    const error = new Error('Não foi possível salvar a sessão com segurança neste aparelho.');
    error.isSessionStorageError = true;
    throw error;
  }
  setAuthToken(token);
};

export const authApi = {
  login: async (email, senha) => {
    const response = await client.post('/auth/login', { email, senha }, { skipAuth: true });
    await saveSession(response.data?.token);
    return response.data;
  },

  register: async (nome, email, senha, tipo = 'GERENTE') => {
    const response = await client.post('/auth/register', { nome, email, senha, tipo }, { skipAuth: true });
    await saveSession(response.data?.token);
    return response.data;
  },

  getMe: async () => {
    const response = await client.get('/auth/me');
    return response.data;
  },

  restoreSession: async () => {
    const token = await sessionStorage.read();
    if (!token) return false;

    setAuthToken(token);
    try {
      await authApi.getMe();
      return true;
    } catch (error) {
      if (authToken === token) setAuthToken(null);
      return false;
    }
  },

  logout: async () => {
    await sessionStorage.clear();
    setAuthToken(null);
  },
};

export const lotesApi = {
  getLotes: async () => {
    const response = await client.get('/lotes');
    return response.data;
  },
};

export const alertasApi = {
  getAtivos: async () => (await client.get('/alertas/vencimento')).data,
  salvarLote: async (alertaId) => (await client.patch(`/alertas/${alertaId}/salvar-lote`)).data,
  marcarCiente: async (alertaId) => (await client.patch(`/alertas/${alertaId}/ciente`)).data,
};

export const promocoesApi = {
  getPendentes: async () => (await client.get('/promocoes/pendentes')).data,
};

export const pdvApi = {
  testarConexao: async (dados) => {
    const response = await client.post('/configuracoes/pdv/testar-conexao', dados);
    return response.data;
  },
};

export default client;
