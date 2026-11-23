import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080',
    headers: {
        'Content-Type': 'application/json'
    }
});

// token JWT em todas as requisições
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('@LucroPlus:token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Lida token expirado
api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            localStorage.removeItem('@LucroPlus:token');
            localStorage.removeItem('@LucroPlus:user');
            window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

export default api;


