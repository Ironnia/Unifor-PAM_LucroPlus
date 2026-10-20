import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';

import LoginScreen from '../screens/LoginScreen';
import HomeScreen from '../screens/HomeScreen';
import EstoqueScreen from '../screens/EstoqueScreen';
import RankingScreen from '../screens/RankingScreen';
import AlertasScreen from '../screens/AlertasScreen';
import { contagemNavegacao, normalizarContagemNavegacao } from '../screens/alertasViewModel';
import PerfilScreen from '../screens/PerfilScreen';
import { alertasApi, authApi, subscribeToUnauthorized } from '../services/api';
import { sessionStorage } from '../services/sessionStorage';

const Tab = createBottomTabNavigator();

export default function AppNavigator() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [restoringSession, setRestoringSession] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);
  const [sessionMessage, setSessionMessage] = useState(null);
  const [alertCount, setAlertCount] = useState(0);
  const { criticos, atencao } = normalizarContagemNavegacao(alertCount);

  useEffect(() => {
    let active = true;
    const unsubscribe = subscribeToUnauthorized((message) => {
      if (!active) return;
      setAlertCount(0);
      setSessionMessage(message);
      setIsAuthenticated(false);
    });
    authApi.restoreSession()
      .then((restored) => {
        if (active) setIsAuthenticated(restored);
      })
      .catch(() => {
      })
      .finally(() => {
        if (active) setRestoringSession(false);
      });
    return () => { active = false; unsubscribe(); };
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;
    alertasApi.getAtivos()
      .then((dados) => { if (active && Array.isArray(dados)) setAlertCount(contagemNavegacao(dados)); })
      .catch(() => { if (active) setAlertCount(0); });
    return () => { active = false; };
  }, [isAuthenticated]);

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await authApi.logout();
      setAlertCount(0);
      setSessionMessage(null);
      setIsAuthenticated(false);

      return true;
    } catch (_) {
      Alert.alert('Não foi possível sair', 'Tente novamente para remover a sessão deste aparelho.');
      return false;
    } finally {
      setLoggingOut(false);
    }
  };

  if (restoringSession) {
    return <View style={styles.loading}><ActivityIndicator color="#6c63ff" size="large" /></View>;
  }

  if (!isAuthenticated) {
    return <LoginScreen sessionMessage={sessionMessage} onLoginSuccess={() => {
      setSessionMessage(null);

      setIsAuthenticated(true);
    }} />;
  }

  return (
    <NavigationContainer>
      <Tab.Navigator
        screenOptions={({ route, navigation }) => ({
          tabBarIcon: ({ focused, color, size }) => {
            let iconName;

            if (route.name === 'Home') {
              iconName = focused ? 'stats-chart' : 'stats-chart-outline';
            } else if (route.name === 'Estoque') {
              iconName = focused ? 'cube' : 'cube-outline';
            } else if (route.name === 'Cardápio') {
              iconName = focused ? 'restaurant' : 'restaurant-outline';
            } else if (route.name === 'Alertas') {
              iconName = focused ? 'notifications' : 'notifications-outline';
            } else if (route.name === 'Perfil') {
              iconName = focused ? 'person' : 'person-outline';
            }

            if (route.name === 'Alertas') return <View style={styles.iconeAlertas} accessibilityLabel={`${atencao} alertas em atenção, ${criticos} críticos`}>
              <Ionicons name={iconName} size={size} color={color} />
              {atencao > 0 && <View style={[styles.badgeAlerta, styles.badgeAtencao]}><Text style={styles.badgeTextoAtencao}>{atencao}</Text></View>}
              {criticos > 0 && <View style={[styles.badgeAlerta, styles.badgeCritico]}><Text style={styles.badgeTextoCritico}>{criticos}</Text></View>}
            </View>;
            return <Ionicons name={iconName} size={size} color={color} />;
          },
          tabBarActiveTintColor: '#6c63ff',
          tabBarInactiveTintColor: '#888',
          tabBarStyle: {
            backgroundColor: '#1a1a2e',
            borderTopColor: '#2a2a4e',
          },
          tabBarLabelStyle: {
            fontSize: 12,
          },
          headerStyle: {
            backgroundColor: '#1a1a2e',
          },
          headerTintColor: '#fff',
          headerTitleStyle: {
            fontWeight: 'bold',
          },
          headerRight: route.name !== 'Home' ? undefined : () => (
            <TouchableOpacity
              onPress={() => navigation.navigate('Perfil')}
              style={{ marginRight: 8, minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }}
              accessibilityLabel="Abrir perfil"
              accessibilityRole="button"
            >
              <Ionicons name="person-circle-outline" size={26} color="#fff" />
            </TouchableOpacity>
          ),
        })}
      >
        <Tab.Screen name="Home" component={HomeScreen} options={{ title: 'Dashboard' }} />
        <Tab.Screen name="Estoque" component={EstoqueScreen} />
        <Tab.Screen name="Cardápio" component={RankingScreen} />
        <Tab.Screen name="Alertas" options={{ title: 'Alertas & Ações', tabBarLabel: 'Alertas' }}>
          {() => <AlertasScreen onCountChange={setAlertCount} />}
        </Tab.Screen>
        <Tab.Screen
          name="Perfil"
          options={({ navigation }) => ({
            title: 'Perfil',
            tabBarButton: () => null,
            tabBarStyle: { display: 'none' },
            headerLeft: () => (
              <TouchableOpacity
                onPress={() => navigation.navigate('Home')}
                style={{ marginLeft: 8, minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }}
                accessibilityLabel="Voltar ao Dashboard"
                accessibilityRole="button"
              >
                <Ionicons name="arrow-back" size={24} color="#fff" />
              </TouchableOpacity>
            ),
          })}
        >
          {() => <PerfilScreen onLogout={handleLogout} />}
        </Tab.Screen>
      </Tab.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0f0f23' },
  iconeAlertas: { width: 28, height: 28, alignItems: 'center', justifyContent: 'center', overflow: 'visible' },
  badgeAlerta: { position: 'absolute', top: -4, minWidth: 16, height: 16, borderRadius: 8, paddingHorizontal: 3, alignItems: 'center', justifyContent: 'center' },
  badgeAtencao: { left: -12, backgroundColor: '#ffb74d' },
  badgeCritico: { right: -12, backgroundColor: '#ff5252' },
  badgeTextoAtencao: { color: '#14142b', fontSize: 10, fontWeight: 'bold' },
  badgeTextoCritico: { color: '#fff', fontSize: 10, fontWeight: 'bold' },
});
