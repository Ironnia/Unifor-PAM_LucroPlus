const ANDROID_EMULATOR_API_URL = 'http://10.0.2.2:8080';

export const normalizeApiBaseUrl = (value) => {
  const normalized = value?.trim().replace(/\/+$/, '');

  if (!normalized || !/^https?:\/\//i.test(normalized)) {
    throw new Error(
      'EXPO_PUBLIC_API_URL deve ser uma URL HTTP(S), por exemplo http://192.168.1.10:8080'
    );
  }

  return normalized;
};

export const API_BASE_URL = normalizeApiBaseUrl(
  process.env.EXPO_PUBLIC_API_URL || ANDROID_EMULATOR_API_URL
);

export const API_URL_SOURCE = process.env.EXPO_PUBLIC_API_URL
  ? 'EXPO_PUBLIC_API_URL'
  : 'fallback do emulador Android';
