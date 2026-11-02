import { Platform } from 'react-native';
import * as LocalAuthentication from 'expo-local-authentication';
import * as Haptics from 'expo-haptics';
 
 
import { setNotificationHandler } from 'expo-notifications/build/NotificationsHandler';
import { getPermissionsAsync, requestPermissionsAsync } from 'expo-notifications/build/NotificationPermissions';
import { AndroidImportance } from 'expo-notifications/build/NotificationChannelManager.types';
import setNotificationChannelAsync from 'expo-notifications/build/setNotificationChannelAsync';
import scheduleNotificationAsync from 'expo-notifications/build/scheduleNotificationAsync';

let notificacoesConfiguradas = false;

export async function verificarSuporteBiometria() {
  if (Platform.OS === 'web') return false;
  try {
    return await LocalAuthentication.hasHardwareAsync()
      && await LocalAuthentication.isEnrolledAsync()
      && (await LocalAuthentication.supportedAuthenticationTypesAsync())
        .includes(LocalAuthentication.AuthenticationType.FINGERPRINT);
  } catch (_) {
    return false;
  }
}

export async function autenticarBiometria(mensagem = 'Confirme sua digital para entrar') {
  if (!await verificarSuporteBiometria()) return false;
  try {
    const resultado = await LocalAuthentication.authenticateAsync({
      promptMessage: mensagem,
      fallbackLabel: 'Usar senha',
      disableDeviceFallback: true,
    });
    return resultado.success === true;
  } catch (_) {
    return false;
  }
}

async function tentarHaptico(acao) {
  if (Platform.OS === 'web') return;
  try { await acao(); } catch (_) {   }
}

export const impactoLeve = () => tentarHaptico(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light));
export const impactoSucesso = () => tentarHaptico(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success));
export const impactoAviso = () => tentarHaptico(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning));

export async function configurarNotificacoes() {
  if (Platform.OS === 'web') return false;
  try {
    if (!notificacoesConfiguradas) {
      setNotificationHandler({
        handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
      });
      notificacoesConfiguradas = true;
    }
    if (Platform.OS === 'android') {
      await setNotificationChannelAsync('lotes-criticos', {
        name: 'Lotes críticos', importance: AndroidImportance.HIGH,
      });
    }
    const permissao = await getPermissionsAsync();
    if (permissao.granted) return true;
    return (await requestPermissionsAsync()).granted === true;
  } catch (_) {
    return false;
  }
}

export async function notificarLoteCritico(nomeIngrediente, diasRestantes) {
  if (!await configurarNotificacoes()) return false;
  try {
    await scheduleNotificationAsync({
      content: {
        title: '⚠️ Atenção no Estoque',
        body: `O insumo ${nomeIngrediente} tem lote crítico vencendo em breve!`,
        data: { diasRestantes },
        sound: 'default',
      },
       
      trigger: Platform.OS === 'android' ? { channelId: 'lotes-criticos' } : null,
    });
    return true;
  } catch (_) {
    return false;
  }
}
