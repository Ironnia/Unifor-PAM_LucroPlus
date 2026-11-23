const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const babel = require('@babel/core');

function carregar({ plataforma = 'android', hardware = true, cadastrada = true, digital = true, autenticacaoBemSucedida = true, permissao = true, falhaHaptica = false } = {}) {
  const chamadas = { autenticacoes: 0, impactos: 0, avisos: 0, notificacoes: [] };
  const mocks = {
    'react-native': { Platform: { OS: plataforma } },
    'expo-local-authentication': {
      AuthenticationType: { FINGERPRINT: 1 },
      hasHardwareAsync: async () => hardware,
      isEnrolledAsync: async () => cadastrada,
      supportedAuthenticationTypesAsync: async () => digital ? [1] : [2],
      authenticateAsync: async () => { chamadas.autenticacoes += 1; return { success: autenticacaoBemSucedida }; },
    },
    'expo-haptics': {
      ImpactFeedbackStyle: { Light: 'Light' }, NotificationFeedbackType: { Success: 'Success', Warning: 'Warning' },
      impactAsync: async () => { chamadas.impactos += 1; if (falhaHaptica) throw Error('sem motor'); },
      notificationAsync: async () => { chamadas.avisos += 1; if (falhaHaptica) throw Error('sem motor'); },
    },
    'expo-notifications': {
      AndroidImportance: { HIGH: 4 },
      setNotificationHandler: (handler) => { chamadas.handler = handler; }, setNotificationChannelAsync: async () => {},
      getPermissionsAsync: async () => ({ granted: permissao }),
      requestPermissionsAsync: async () => ({ granted: permissao }),
      scheduleNotificationAsync: async (notification) => { chamadas.notificacoes.push(notification); },
    },
  };
  const avisos = mocks['expo-notifications'];
  delete mocks['expo-notifications'];
  Object.assign(mocks, {
    'expo-notifications/build/NotificationsHandler': { setNotificationHandler: avisos.setNotificationHandler },
    'expo-notifications/build/NotificationPermissions': {
      getPermissionsAsync: avisos.getPermissionsAsync, requestPermissionsAsync: avisos.requestPermissionsAsync,
    },
    'expo-notifications/build/NotificationChannelManager.types': { AndroidImportance: avisos.AndroidImportance },
    'expo-notifications/build/setNotificationChannelAsync': avisos.setNotificationChannelAsync,
    'expo-notifications/build/scheduleNotificationAsync': avisos.scheduleNotificationAsync,
  });
  const source = fs.readFileSync(path.join(__dirname, '../src/services/nativeService.js'), 'utf8');
  const compiled = babel.transformSync(source, {
    babelrc: false, configFile: false, plugins: ['@babel/plugin-transform-modules-commonjs'],
  }).code;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)((name) => {
    assert.ok(name in mocks, `Importação não permitida no serviço local: ${name}`);
    return mocks[name];
  }, module, module.exports);
  return { service: module.exports, chamadas };
}

test('digital cadastrada autentica; cancelamento não libera a entrada', async () => {
  const valido = carregar();
  assert.equal(await valido.service.verificarSuporteBiometria(), true);
  assert.equal(await valido.service.autenticarBiometria('Entre'), true);
  assert.equal(valido.chamadas.autenticacoes, 1);
  const cancelado = carregar({ autenticacaoBemSucedida: false });
  assert.equal(await cancelado.service.autenticarBiometria(), false);
  assert.equal(cancelado.chamadas.autenticacoes, 1);
});

test('sem sensor ou sem digital cadastrada, login biométrico fica indisponível', async () => {
  for (const opcoes of [{ hardware: false }, { cadastrada: false }, { plataforma: 'web' }]) {
    const { service, chamadas } = carregar(opcoes);
    assert.equal(await service.verificarSuporteBiometria(), false);
    assert.equal(await service.autenticarBiometria(), false);
    assert.equal(chamadas.autenticacoes, 0);
  }
});

test('falha de hápticos não interrompe as ações', async () => {
  const { service, chamadas } = carregar({ falhaHaptica: true });
  await service.impactoLeve();
  await service.impactoSucesso();
  await service.impactoAviso();
  assert.equal(chamadas.impactos, 1);
  assert.equal(chamadas.avisos, 2);
  await carregar({ plataforma: 'web' }).service.impactoLeve();
});

test('alerta crítico agenda aviso local com permissão', async () => {
  const { service, chamadas } = carregar();
  assert.equal(await service.notificarLoteCritico('Tomate', 2), true);
  assert.equal(chamadas.notificacoes.length, 1);
  assert.equal(chamadas.notificacoes[0].content.title, '⚠️ Atenção no Estoque');
  assert.match(chamadas.notificacoes[0].content.body, /Tomate/);
  assert.deepEqual(chamadas.notificacoes[0].trigger, { channelId: 'lotes-criticos' });
  assert.deepEqual(await chamadas.handler.handleNotification(), {
    shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false,
  });
});

test('sem permissão, aviso não é agendado e a tela pode continuar', async () => {
  const { service, chamadas } = carregar({ permissao: false });
  assert.equal(await service.notificarLoteCritico('Tomate', 2), false);
  assert.equal(chamadas.notificacoes.length, 0);
});

test('módulos reais de aviso local não carregam registro de push na inicialização', () => {
  const visitados = new Set();
  function verificar(arquivo) {
    if (visitados.has(arquivo)) return;
    visitados.add(arquivo);
    assert.doesNotMatch(arquivo, /DevicePushTokenAutoRegistration|TokenEmitter|warnOfExpoGoPushUsage/);
    const ast = babel.parseSync(fs.readFileSync(arquivo, 'utf8'), { babelrc: false, configFile: false });
    for (const declaracao of ast.program.body) {
      if (!declaracao.source) continue;
      const origem = declaracao.source.value;
      if (origem.startsWith('expo-notifications')) {
        verificar(require.resolve(origem));
      } else if (origem.startsWith('.') && arquivo.includes('expo-notifications')) {
        verificar(require.resolve(path.resolve(path.dirname(arquivo), origem)));
      }
    }
  }
  verificar(path.join(__dirname, '../src/services/nativeService.js'));
});
