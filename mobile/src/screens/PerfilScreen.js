import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';

import appInfo from '../../app.json';
import { authApi } from '../services/api';
import { montarPerfil } from './perfilViewModel';

const SUPORTE_URL = 'mailto:jvpc@edu.unifor.br?subject=Suporte%20acad%C3%AAmico%20LucroPlus';

export default function PerfilScreen({ onLogout }) {
  const [usuario, setUsuario] = useState(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState('');
  const [saindo, setSaindo] = useState(false);

  const carregarPerfil = useCallback(async () => {
    setCarregando(true);
    setErro('');
    try {
      setUsuario(await authApi.getMe());
    } catch (error) {
      if (error.response?.status !== 401) {
        setErro('Não foi possível carregar os dados da conta. Verifique sua conexão.');
      }
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    carregarPerfil();
  }, [carregarPerfil]);

  const abrirSuporte = async () => {
    try {
      await Linking.openURL(SUPORTE_URL);
    } catch (_) {
      Alert.alert('Suporte acadêmico', 'Envie um e-mail para jvpc@edu.unifor.br.');
    }
  };

  const sairDaConta = async () => {
    if (saindo) return;
    setSaindo(true);
    const encerrou = await onLogout?.();
    if (!encerrou) setSaindo(false);
  };

  if (carregando) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator color="#6c63ff" size="large" />
        <Text style={styles.loadingText}>Carregando perfil...</Text>
      </View>
    );
  }

  if (erro) {
    return (
      <View style={styles.centered}>
        <Ionicons name="cloud-offline-outline" size={42} color="#ffb74d" />
        <Text style={styles.errorText}>{erro}</Text>
        <TouchableOpacity style={styles.retryButton} onPress={carregarPerfil} accessibilityRole="button" accessibilityLabel="Tentar carregar perfil novamente">
          <Text style={styles.retryButtonText}>Tentar novamente</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const perfil = montarPerfil(usuario);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.avatar}>
        <Ionicons name="person" size={44} color="#a9a4ff" />
      </View>
      <Text style={styles.name}>{perfil.nome}</Text>
      <Text style={styles.email}>{perfil.email}</Text>

      <View style={styles.unitCard}>
        <View style={styles.unitHeader}>
          <Ionicons
            name={perfil.acessoGlobal ? 'shield-checkmark-outline' : 'restaurant-outline'}
            size={24}
            color={perfil.acessoGlobal ? '#b0b0c8' : '#8d86ff'}
          />
          <Text style={styles.unitTitle}>Unidade ativa</Text>
        </View>
        <Text style={styles.restaurantName}>{perfil.restaurante}</Text>
        <View style={[styles.badge, perfil.acessoGlobal && styles.neutralBadge]}>
          <Text style={[styles.badgeText, perfil.acessoGlobal && styles.neutralBadgeText]}>
            {perfil.tipo}
          </Text>
        </View>
        {perfil.semRestaurante && !perfil.acessoGlobal ? (
          <Text style={styles.unitWarning}>Esta conta ainda não possui uma unidade vinculada.</Text>
        ) : null}
      </View>

      <View style={styles.actionsCard}>
        <TouchableOpacity style={styles.actionButton} onPress={abrirSuporte} accessibilityRole="button" accessibilityLabel="Falar com o Suporte">
          <Ionicons name="mail-outline" size={22} color="#8d86ff" />
          <Text style={styles.actionText}>Falar com o Suporte</Text>
          <Ionicons name="open-outline" size={18} color="#777792" />
        </TouchableOpacity>

        <View style={styles.divider} />

        <TouchableOpacity
          style={styles.actionButton}
          onPress={sairDaConta}
          disabled={saindo}
          accessibilityRole="button"
          accessibilityLabel="Sair da Conta"
          accessibilityState={{ disabled: saindo }}
        >
          {saindo ? (
            <ActivityIndicator color="#ff6b6b" size="small" />
          ) : (
            <Ionicons name="log-out-outline" size={22} color="#ff6b6b" />
          )}
          <Text style={styles.logoutText}>{saindo ? 'Encerrando sessão...' : 'Sair da Conta'}</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.version}>LucroPlus v{appInfo.expo.version}</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  content: { padding: 24, alignItems: 'center', paddingBottom: 40 },
  centered: {
    flex: 1,
    backgroundColor: '#0f0f23',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  loadingText: { color: '#a3a3bd', marginTop: 12 },
  avatar: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: '#1e1e38',
    borderWidth: 1,
    borderColor: '#393568',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 16,
    marginBottom: 16,
  },
  name: { color: '#fff', fontSize: 24, fontWeight: 'bold', textAlign: 'center' },
  email: { color: '#9a9ab3', fontSize: 14, marginTop: 6, textAlign: 'center' },
  unitCard: {
    width: '100%',
    backgroundColor: '#18182e',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#302d59',
    padding: 16,
    marginTop: 28,
  },
  unitHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 14 },
  unitTitle: { color: '#aaaac3', fontSize: 13, marginLeft: 9, textTransform: 'uppercase' },
  restaurantName: { color: '#fff', fontSize: 20, fontWeight: 'bold' },
  badge: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(108, 99, 255, 0.18)',
    borderColor: '#6c63ff',
    borderWidth: 1,
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 6,
    marginTop: 14,
  },
  badgeText: { color: '#b9b5ff', fontSize: 12, fontWeight: 'bold' },
  neutralBadge: { backgroundColor: '#27273b', borderColor: '#55556d' },
  neutralBadgeText: { color: '#c0c0d0' },
  unitWarning: { color: '#ffb74d', fontSize: 12, lineHeight: 18, marginTop: 12 },
  actionsCard: {
    width: '100%',
    backgroundColor: '#18182e',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#292947',
    marginTop: 18,
    overflow: 'hidden',
  },
  actionButton: {
    minHeight: 58,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 18,
  },
  actionText: { color: '#ededf7', fontSize: 15, flex: 1, marginLeft: 12 },
  logoutText: { color: '#ff7676', fontSize: 15, fontWeight: '600', marginLeft: 12 },
  divider: { height: 1, backgroundColor: '#292947', marginHorizontal: 18 },
  version: { color: '#aaaac1', fontSize: 12, marginTop: 24 },
  errorText: { color: '#d7d7e4', textAlign: 'center', lineHeight: 20, marginTop: 14 },
  retryButton: {
    backgroundColor: '#5b50dc',
    borderRadius: 10,
    paddingHorizontal: 18,
    paddingVertical: 11,
    minHeight: 44,
    justifyContent: 'center',
    marginTop: 18,
  },
  retryButtonText: { color: '#fff', fontWeight: 'bold' },
});
