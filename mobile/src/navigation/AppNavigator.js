import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, TouchableOpacity, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';

import LoginScreen from '../screens/LoginScreen';
import HomeScreen from '../screens/HomeScreen';
import EstoqueScreen from '../screens/EstoqueScreen';
import AlertasScreen from '../screens/AlertasScreen';
import ConfiguracoesScreen from '../screens/ConfiguracoesScreen';
import { alertasApi, authApi, subscribeToUnauthorized } from '../services/api';

const Tab = createBottomTabNavigator();

export default function AppNavigator() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [restoringSession, setRestoringSession] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);
  const [sessionMessage, setSessionMessage] = useState(null);
  const [alertCount, setAlertCount] = useState(0);

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
      .catch(() => {})
      .finally(() => {
        if (active) setRestoringSession(false);
      });
    return () => { active = false; unsubscribe(); };
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;
    alertasApi.getAtivos()
      .then((dados) => { if (active && Array.isArray(dados)) setAlertCount(dados.length); })
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
    } catch (_) {
      Alert.alert('Não foi possível sair', 'Tente novamente para remover a sessão deste aparelho.');
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
        screenOptions={({ route }) => ({
          tabBarIcon: ({ focused, color, size }) => {
            let iconName;

            if (route.name === 'Home') {
              iconName = focused ? 'home' : 'home-outline';
            } else if (route.name === 'Estoque') {
              iconName = focused ? 'cube' : 'cube-outline';
            } else if (route.name === 'Alertas') {
              iconName = focused ? 'notifications' : 'notifications-outline';
            } else if (route.name === 'Configurações') {
              iconName = focused ? 'settings' : 'settings-outline';
            }

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
          headerRight: () => (
            <TouchableOpacity onPress={handleLogout} disabled={loggingOut} style={{ marginRight: 16 }}>
              <Ionicons name="log-out-outline" size={22} color="#ff5252" />
            </TouchableOpacity>
          ),
        })}
      >
        <Tab.Screen name="Home" component={HomeScreen} options={{ title: 'Dashboard' }} />
        <Tab.Screen name="Estoque" component={EstoqueScreen} />
        <Tab.Screen name="Alertas" options={{ title: 'Alertas & Ações', tabBarLabel: 'Alertas', tabBarBadge: alertCount > 0 ? alertCount : undefined }}>
          {() => <AlertasScreen onCountChange={setAlertCount} />}
        </Tab.Screen>
        <Tab.Screen name="Configurações" component={ConfiguracoesScreen} />
      </Tab.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0f0f23' },
});
