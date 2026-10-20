import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import AsyncStorage from '@react-native-async-storage/async-storage';

// O JWT é um segredo pequeno. No aparelho, o SecureStore usa o armazenamento protegido
// do sistema operacional. No navegador não há equivalente seguro, então a sessão web
const TOKEN_KEY = 'lucroplus.jwt.v1';
const BIOMETRIA_KEY = 'lucroplus.biometria.v1';
const LEGACY_SESSION_KEYS = ['@LucroPlus:token', '@LucroPlus:usuario'];

export const sessionStorage = {
  read: () => Platform.OS === 'web' ? Promise.resolve(null) : SecureStore.getItemAsync(TOKEN_KEY),
  write: (token) => Platform.OS === 'web' ? Promise.resolve() : SecureStore.setItemAsync(TOKEN_KEY, token),
  biometriaAtiva: async () => Platform.OS !== 'web' && await SecureStore.getItemAsync(BIOMETRIA_KEY) === 'true',
  ativarBiometria: () => Platform.OS === 'web' ? Promise.resolve() : SecureStore.setItemAsync(BIOMETRIA_KEY, 'true'),
  clear: async () => {
    // A versão atual usa SecureStore. As chaves antigas também são removidas para
    await Promise.all([
      Platform.OS === 'web' ? Promise.resolve() : SecureStore.deleteItemAsync(TOKEN_KEY),
      Platform.OS === 'web' ? Promise.resolve() : SecureStore.deleteItemAsync(BIOMETRIA_KEY),
      AsyncStorage.multiRemove(LEGACY_SESSION_KEYS),
    ]);
  },
};
