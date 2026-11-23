import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export default function ConfiguracoesScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>⚙️ Configurações</Text>
      <Text style={styles.subtitle}>Integração PDV e preferências</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f0f23',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#888',
  },
});
