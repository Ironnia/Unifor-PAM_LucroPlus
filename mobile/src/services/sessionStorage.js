import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

// O JWT é um segredo pequeno. No aparelho, o SecureStore usa o armazenamento protegido
// do sistema operacional. No navegador não há equivalente seguro, então a sessão web
// permanece somente na memória e exige novo login após recarregar a página.
const TOKEN_KEY = 'lucroplus.jwt.v1';

export const sessionStorage = {
  read: () => Platform.OS === 'web' ? Promise.resolve(null) : SecureStore.getItemAsync(TOKEN_KEY),
  write: (token) => Platform.OS === 'web' ? Promise.resolve() : SecureStore.setItemAsync(TOKEN_KEY, token),
  clear: () => Platform.OS === 'web' ? Promise.resolve() : SecureStore.deleteItemAsync(TOKEN_KEY),
};
