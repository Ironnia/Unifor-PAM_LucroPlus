import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
  TouchableOpacity,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { lotesApi } from '../services/api';
import { CriticidadeLote } from '../types/apiTypes';

const obterMensagemErro = (error) => {
  if (error.code === 'ECONNABORTED') {
    return 'A API demorou para responder. Verifique a rede e tente novamente.';
  }

  if (error.message === 'A API retornou um formato inesperado para a lista de lotes.') {
    return error.message;
  }

  return error.response?.data?.erro || 'Não foi possível carregar os lotes. Verifique a conexão com a API.';
};

export default function EstoqueScreen() {
  const [lotes, setLotes] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [filtro, setFiltro] = useState('TODOS');
  const [erro, setErro] = useState('');
  const [avisoAtualizacao, setAvisoAtualizacao] = useState('');

  const carregarLotes = useCallback(async ({ preservarDados = false } = {}) => {
    setErro('');
    setAvisoAtualizacao('');

    try {
      const dados = await lotesApi.getLotes();

      if (!Array.isArray(dados)) {
        throw new Error('A API retornou um formato inesperado para a lista de lotes.');
      }

      setLotes(dados);
    } catch (error) {
      const mensagem = obterMensagemErro(error);

      if (preservarDados) {
        setAvisoAtualizacao(`${mensagem} Os dados anteriores foram mantidos.`);
      } else {
        setLotes([]);
        setErro(mensagem);
      }
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useEffect(() => {
    carregarLotes();
  }, [carregarLotes]);

  const onRefresh = () => {
    setAtualizando(true);
    carregarLotes({ preservarDados: true });
  };

  const tentarNovamente = () => {
    setCarregando(true);
    carregarLotes();
  };

  const lotesFiltrados = lotes.filter((lote) => {
    if (filtro === 'CRITICO') return lote.criticidade === CriticidadeLote.CRITICO;
    if (filtro === 'ATENCAO') return lote.criticidade === CriticidadeLote.ATENCAO;
    if (filtro === 'SEGURO') return lote.criticidade === CriticidadeLote.SEGURO;
    return true;
  });

  const obterBadgeInfo = (criticidade, dias) => {
    if (criticidade === CriticidadeLote.CRITICO) {
      return {
        texto: dias <= 0 ? 'Vence HOJE' : dias === 1 ? '1 dia restante' : `${dias} dias restantes`,
        corTexto: '#ff5252',
        bg: 'rgba(255, 82, 82, 0.15)',
        borda: '#ff5252',
        icone: 'alert-circle',
      };
    }
    if (criticidade === CriticidadeLote.ATENCAO) {
      return {
        texto: `${dias} dias restantes`,
        corTexto: '#ffb74d',
        bg: 'rgba(255, 183, 77, 0.15)',
        borda: '#ffb74d',
        icone: 'warning',
      };
    }
    return {
      texto: `${dias} dias restantes`,
      corTexto: '#00e676',
      bg: 'rgba(0, 230, 118, 0.15)',
      borda: '#00e676',
      icone: 'shield-checkmark',
    };
  };

  const renderItem = ({ item }) => {
    const badge = obterBadgeInfo(item.criticidade, item.diasRestantes);
    const custoPorGrama = Number(item.custoUnitario);

    return (
      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <View style={styles.headerLeft}>
            <Text style={styles.ingredienteNome}>{item.ingredienteNome}</Text>
            <Text style={styles.numeroLote}>{item.numeroLote}</Text>
          </View>
          <View style={[styles.badge, { backgroundColor: badge.bg, borderColor: badge.borda }]}>
            <Ionicons name={badge.icone} size={14} color={badge.corTexto} style={{ marginRight: 4 }} />
            <Text style={[styles.badgeText, { color: badge.corTexto }]}>{badge.texto}</Text>
          </View>
        </View>

        <View style={styles.cardDivider} />

        <View style={styles.cardBody}>
          <View style={styles.infoCol}>
            <Text style={styles.infoLabel}>Estoque Atual</Text>
            <Text style={styles.infoValue}>{item.quantidadeFormatada}</Text>
          </View>
          <View style={styles.infoCol}>
            <Text style={styles.infoLabel}>Validade</Text>
            <Text style={styles.infoValue}>{item.dataValidade}</Text>
          </View>
          <View style={styles.infoCol}>
            <Text style={styles.infoLabel}>Custo/g</Text>
            <Text style={styles.infoValue}>
              {Number.isFinite(custoPorGrama) ? `R$ ${custoPorGrama.toFixed(4)}` : 'Não informado'}
            </Text>
          </View>
        </View>
      </View>
    );
  };

  if (carregando) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#6c63ff" />
        <Text style={styles.loadingText}>Carregando lotes do estoque...</Text>
      </View>
    );
  }

  if (erro) {
    return (
      <View style={styles.centerContainer}>
        <Ionicons name="cloud-offline-outline" size={52} color="#ff5252" />
        <Text style={styles.emptyTitle}>Falha ao carregar o estoque</Text>
        <Text style={styles.errorText}>{erro}</Text>
        <TouchableOpacity style={styles.retryButton} onPress={tentarNovamente}>
          <Ionicons name="refresh" size={18} color="#ffffff" />
          <Text style={styles.retryButtonText}>Tentar novamente</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.filterRow}>
        {['TODOS', 'CRITICO', 'ATENCAO', 'SEGURO'].map((f) => (
          <TouchableOpacity
            key={f}
            style={[styles.filterChip, filtro === f && styles.filterChipActive]}
            onPress={() => setFiltro(f)}
          >
            <Text style={[styles.filterChipText, filtro === f && styles.filterChipTextActive]}>
              {f === 'TODOS' ? 'Todos' : f === 'CRITICO' ? '🔴 Críticos' : f === 'ATENCAO' ? '🟡 Atenção' : '🟢 Seguros'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {avisoAtualizacao ? (
        <View style={styles.refreshWarning}>
          <Ionicons name="warning-outline" size={18} color="#ffb74d" />
          <Text style={styles.refreshWarningText}>{avisoAtualizacao}</Text>
          <TouchableOpacity onPress={onRefresh}>
            <Text style={styles.refreshWarningAction}>Repetir</Text>
          </TouchableOpacity>
        </View>
      ) : null}

      <FlatList
        data={lotesFiltrados}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={atualizando} onRefresh={onRefresh} tintColor="#6c63ff" />}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Ionicons name="file-tray-outline" size={48} color="#555" />
            <Text style={styles.emptyTitle}>Nenhum lote encontrado</Text>
            <Text style={styles.emptySubtitle}>
              {lotes.length === 0
                ? 'O estoque ainda não possui lotes cadastrados.'
                : 'Não há insumos com o filtro selecionado.'}
            </Text>
          </View>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f0f23',
  },
  centerContainer: {
    flex: 1,
    backgroundColor: '#0f0f23',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  loadingText: {
    color: '#8b8ba7',
    marginTop: 12,
    fontSize: 14,
  },
  errorText: {
    color: '#b8b8cc',
    fontSize: 14,
    lineHeight: 20,
    marginTop: 8,
    maxWidth: 320,
    textAlign: 'center',
  },
  retryButton: {
    alignItems: 'center',
    backgroundColor: '#6c63ff',
    borderRadius: 8,
    flexDirection: 'row',
    marginTop: 20,
    paddingHorizontal: 18,
    paddingVertical: 10,
  },
  retryButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: 'bold',
    marginLeft: 8,
  },
  filterRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#14142b',
    borderBottomWidth: 1,
    borderBottomColor: '#252545',
  },
  filterChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#1f1f3a',
    marginRight: 8,
    borderWidth: 1,
    borderColor: '#303055',
  },
  filterChipActive: {
    backgroundColor: '#6c63ff',
    borderColor: '#6c63ff',
  },
  filterChipText: {
    color: '#8b8ba7',
    fontSize: 12,
    fontWeight: '500',
  },
  filterChipTextActive: {
    color: '#ffffff',
    fontWeight: 'bold',
  },
  refreshWarning: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 183, 77, 0.12)',
    borderBottomColor: 'rgba(255, 183, 77, 0.35)',
    borderBottomWidth: 1,
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  refreshWarningText: {
    color: '#ffd29a',
    flex: 1,
    fontSize: 12,
    lineHeight: 17,
    marginHorizontal: 8,
  },
  refreshWarningAction: {
    color: '#ffb74d',
    fontSize: 12,
    fontWeight: 'bold',
  },
  listContent: {
    padding: 16,
  },
  card: {
    backgroundColor: '#18182e',
    borderRadius: 14,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#262646',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  headerLeft: {
    flex: 1,
    marginRight: 8,
  },
  ingredienteNome: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ffffff',
  },
  numeroLote: {
    fontSize: 12,
    color: '#8b8ba7',
    marginTop: 2,
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    borderWidth: 1,
  },
  badgeText: {
    fontSize: 11,
    fontWeight: 'bold',
  },
  cardDivider: {
    height: 1,
    backgroundColor: '#262646',
    marginVertical: 12,
  },
  cardBody: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  infoCol: {
    flex: 1,
  },
  infoLabel: {
    fontSize: 11,
    color: '#8b8ba7',
    marginBottom: 4,
  },
  infoValue: {
    fontSize: 14,
    fontWeight: '600',
    color: '#e0e0ff',
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 64,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#ffffff',
    marginTop: 12,
  },
  emptySubtitle: {
    fontSize: 13,
    color: '#8b8ba7',
    marginTop: 4,
    textAlign: 'center',
  },
});
