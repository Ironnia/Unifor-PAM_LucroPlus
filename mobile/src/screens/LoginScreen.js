import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { authApi } from '../services/api';

export default function LoginScreen({ onLoginSuccess }) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [mostrarSenha, setMostrarSenha] = useState(false);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState('');

  const [modoRegistro, setModoRegistro] = useState(false);
  const [nome, setNome] = useState('');

  const validarFormulario = () => {
    setErro('');
    if (modoRegistro && !nome.trim()) {
      setErro('Informe seu nome completo.');
      return false;
    }
    if (!email.trim()) {
      setErro('Informe seu endereço de email.');
      return false;
    }
    if (!email.includes('@') || !email.includes('.')) {
      setErro('Digite um email válido.');
      return false;
    }
    if (!senha || senha.length < 6) {
      setErro('A senha deve possuir pelo menos 6 caracteres.');
      return false;
    }
    return true;
  };

  const handleSubmeter = async () => {
    if (!validarFormulario()) return;

    setCarregando(true);
    setErro('');

    try {
      if (modoRegistro) {
        await authApi.register(nome, email, senha);
      } else {
        await authApi.login(email, senha);
      }

      if (onLoginSuccess) {
        onLoginSuccess();
      }
    } catch (error) {
      const msg = error.response?.data?.erro || (error.isSessionStorageError
        ? error.message
        : 'Falha ao conectar com o servidor. Verifique sua conexão.');
      setErro(msg);
    } finally {
      setCarregando(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        <View style={styles.logoContainer}>
          <View style={styles.logoBadge}>
            <Ionicons name="restaurant" size={38} color="#6c63ff" />
          </View>
          <Text style={styles.appTitle}>Lucro<Text style={styles.appTitleHighlight}>Plus</Text></Text>
          <Text style={styles.appSubtitle}>Inteligência Antidesperdício & Lucratividade</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>{modoRegistro ? 'Criar Conta' : 'Acessar Painel'}</Text>
          <Text style={styles.cardSubtitle}>
            {modoRegistro
              ? 'Preencha os dados para cadastrar seu restaurante'
              : 'Entre com suas credenciais de gerente'}
          </Text>

          {erro ? (
            <View style={styles.errorBox}>
              <Ionicons name="alert-circle" size={20} color="#ff5252" />
              <Text style={styles.errorText}>{erro}</Text>
            </View>
          ) : null}

          {modoRegistro ? (
            <View style={styles.inputGroup}>
              <Text style={styles.label}>Nome Completo</Text>
              <View style={styles.inputWrapper}>
                <Ionicons name="person-outline" size={20} color="#888" style={styles.inputIcon} />
                <TextInput
                  style={styles.input}
                  placeholder="Seu nome"
                  placeholderTextColor="#666"
                  value={nome}
                  onChangeText={setNome}
                  autoCapitalize="words"
                />
              </View>
            </View>
          ) : null}

          <View style={styles.inputGroup}>
            <Text style={styles.label}>Email</Text>
            <View style={styles.inputWrapper}>
              <Ionicons name="mail-outline" size={20} color="#888" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="gerente@restaurante.com"
                placeholderTextColor="#666"
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
              />
            </View>
          </View>

          <View style={styles.inputGroup}>
            <Text style={styles.label}>Senha</Text>
            <View style={styles.inputWrapper}>
              <Ionicons name="lock-closed-outline" size={20} color="#888" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="••••••••"
                placeholderTextColor="#666"
                value={senha}
                onChangeText={setSenha}
                secureTextEntry={!mostrarSenha}
                autoCapitalize="none"
              />
              <TouchableOpacity onPress={() => setMostrarSenha(!mostrarSenha)} style={styles.eyeBtn}>
                <Ionicons name={mostrarSenha ? 'eye-off-outline' : 'eye-outline'} size={20} color="#888" />
              </TouchableOpacity>
            </View>
          </View>

          <TouchableOpacity
            style={[styles.primaryButton, carregando && styles.buttonDisabled]}
            onPress={handleSubmeter}
            disabled={carregando}
          >
            {carregando ? (
              <ActivityIndicator color="#fff" size="small" />
            ) : (
              <Text style={styles.primaryButtonText}>{modoRegistro ? 'Cadastrar e Entrar' : 'Entrar'}</Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.toggleModeBtn}
            onPress={() => {
              setModoRegistro(!modoRegistro);
              setErro('');
            }}
          >
            <Text style={styles.toggleModeText}>
              {modoRegistro
                ? 'Já possui acesso? Entre aqui'
                : 'Novo gerente? Crie uma conta'}
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f0f23',
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
  },
  logoContainer: {
    alignItems: 'center',
    marginBottom: 32,
  },
  logoBadge: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#1e1e38',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#30305a',
  },
  appTitle: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#ffffff',
    letterSpacing: 0.5,
  },
  appTitleHighlight: {
    color: '#6c63ff',
  },
  appSubtitle: {
    fontSize: 13,
    color: '#8b8ba7',
    marginTop: 4,
    textAlign: 'center',
  },
  card: {
    backgroundColor: '#18182e',
    borderRadius: 16,
    padding: 24,
    borderWidth: 1,
    borderColor: '#262646',
  },
  cardTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#ffffff',
    marginBottom: 4,
  },
  cardSubtitle: {
    fontSize: 13,
    color: '#8b8ba7',
    marginBottom: 20,
  },
  errorBox: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 82, 82, 0.12)',
    borderWidth: 1,
    borderColor: '#ff5252',
    padding: 12,
    borderRadius: 8,
    marginBottom: 16,
  },
  errorText: {
    color: '#ff5252',
    fontSize: 13,
    marginLeft: 8,
    flex: 1,
  },
  inputGroup: {
    marginBottom: 16,
  },
  label: {
    color: '#c2c2d6',
    fontSize: 13,
    marginBottom: 6,
    fontWeight: '500',
  },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#101020',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#2e2e50',
    paddingHorizontal: 12,
  },
  inputIcon: {
    marginRight: 8,
  },
  input: {
    flex: 1,
    color: '#ffffff',
    fontSize: 14,
    paddingVertical: 12,
  },
  eyeBtn: {
    padding: 6,
  },
  primaryButton: {
    backgroundColor: '#6c63ff',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: 'bold',
  },
  toggleModeBtn: {
    marginTop: 16,
    alignItems: 'center',
  },
  toggleModeText: {
    color: '#8b8ba7',
    fontSize: 13,
  },
});
