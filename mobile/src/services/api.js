import axios from 'axios';
import { API_BASE_URL } from '../config/environment';
import { sessionStorage } from './sessionStorage';

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000, // 10 segundos de timeout para evitar travamento de UI
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

let authToken = null;

// atualiza o token jwt no cabecalho do axios
export const setAuthToken = (token) => {
  authToken = token;
  if (token) {
    client.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  } else {
    delete client.defaults.headers.common['Authorization'];
  }
};

export const getAuthToken = () => authToken;

const saveSession = async (token) => {
  if (!token) {
    throw new Error('O servidor não retornou um token de acesso.');
  }

  try {
    // Só liberamos a navegação depois que a gravação persistente terminar.
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
    const response = await client.post('/auth/login', { email, senha });
    await saveSession(response.data?.token);
    return response.data;
  },

  register: async (nome, email, senha, tipo = 'GERENTE') => {
    const response = await client.post('/auth/register', { nome, email, senha, tipo });
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
      // O servidor verifica assinatura e validade. Um token salvo não basta para entrar.
      await authApi.getMe();
      return true;
    } catch (error) {
      setAuthToken(null);
      if (error.response?.status === 401) {
        await sessionStorage.clear();
      }
      // Falhas de rede não apagam um token possivelmente válido. O próximo início tenta de novo.
      return false;
    }
  },

  logout: async () => {
    // Se a remoção falhar, mantemos a sessão visível para não simular um logout incompleto.
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

export const pdvApi = {
  testarConexao: async (dados) => {
    const response = await client.post('/configuracoes/pdv/testar-conexao', dados);
    return response.data;
  },
};

export default client;
