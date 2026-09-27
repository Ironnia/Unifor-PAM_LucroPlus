import axios from 'axios';
import { API_BASE_URL } from '../config/environment';

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

let authToken = null;

export const setAuthToken = (token) => {
  authToken = token;
  if (token) {
    client.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  } else {
    delete client.defaults.headers.common['Authorization'];
  }
};

export const getAuthToken = () => authToken;

export const authApi = {
  login: async (email, senha) => {
    const response = await client.post('/auth/login', { email, senha });
    if (response.data?.token) {
      setAuthToken(response.data.token);
    }
    return response.data;
  },

  register: async (nome, email, senha, tipo = 'GERENTE') => {
    const response = await client.post('/auth/register', { nome, email, senha, tipo });
    if (response.data?.token) {
      setAuthToken(response.data.token);
    }
    return response.data;
  },

  getMe: async () => {
    const response = await client.get('/auth/me');
    return response.data;
  },

  logout: () => {
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
