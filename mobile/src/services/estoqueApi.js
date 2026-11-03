import client from './api';

export const estoqueApi = {
  getPrevisaoCompras: async () => {
    const response = await client.get('/estoque/previsao-compras');
    return response.data;
  },
};
