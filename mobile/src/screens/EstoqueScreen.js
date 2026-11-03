import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  ActivityIndicator,
  Alert,
  RefreshControl,
  TouchableOpacity,
  TextInput,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { lotesApi } from '../services/api';
import { CriticidadeLote } from '../types/apiTypes';
import PrevisaoComprasSubScreen from './PrevisaoComprasSubScreen';
import HistoricoComprasModal from '../components/HistoricoComprasModal';
import { formatarData } from './alertasViewModel';


































const obterMensagemErro = (error) => {
  if (error.message?.startsWith('Não foi possível conectar ao servidor.')) return error.message;
  if (error.code === 'ECONNABORTED') {
    return 'A API demorou para responder. Verifique a rede e tente novamente.';
  }

  if (error.message === 'A API retornou um formato inesperado para a lista de lotes.') {
    return error.message;
  }

  return error.response?.data?.erro || 'Não foi possível carregar os lotes. Verifique a conexão com a API.';
};

function EstoqueAtualContent() {
  const [lotes, setLotes] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [filtro, setFiltro] = useState('TODOS');
  const [busca, setBusca] = useState('');
  const [loteHistorico, setLoteHistorico] = useState(null);
  const [erro, setErro] = useState('');
  const [avisoAtualizacao, setAvisoAtualizacao] = useState('');

  const carregarLotes = useCallback(async ({ preservarDados = false, avisar = false } = {}) => {
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
      if (avisar) Alert.alert('Não foi possível atualizar', mensagem);

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
    carregarLotes({ preservarDados: true, avisar: true });
  };

  const tentarNovamente = () => {
    setCarregando(true);
    carregarLotes();
  };

  const lotesDaBusca = lotes.filter((lote) => lote.ingredienteNome?.toLocaleLowerCase('pt-BR').includes(busca.trim().toLocaleLowerCase('pt-BR')));
  const contagens = { TODOS: lotesDaBusca.length, CRITICO: 0, ATENCAO: 0, SAUDAVEL: 0 };
  lotesDaBusca.forEach((lote) => { if (contagens[lote.criticidade] != null) contagens[lote.criticidade] += 1; });
  const lotesFiltrados = lotesDaBusca.filter((lote) => {
    if (filtro === 'CRITICO') return lote.criticidade === CriticidadeLote.CRITICO;
    if (filtro === 'ATENCAO') return lote.criticidade === CriticidadeLote.ATENCAO;
    if (filtro === 'SAUDAVEL') return lote.criticidade === CriticidadeLote.SAUDAVEL;
    return true;
  });

  const obterBadgeInfo = (criticidade, dias) => {
    if (criticidade === CriticidadeLote.CRITICO) {
      return {
        texto: dias < 0 ? `Vencido há ${Math.abs(dias)} dia(s)` : dias === 0 ? 'Vence hoje' : dias === 1 ? 'Vence amanhã' : `${dias} dias até vencer`,
        corTexto: '#ff5252',
        bg: 'rgba(255, 82, 82, 0.15)',
        borda: '#ff5252',
        icone: 'alert-circle',
      };
    }
    if (criticidade === CriticidadeLote.ATENCAO) {
      return {
        texto: `${dias} dias até vencer`,
        corTexto: '#ffb74d',
        bg: 'rgba(255, 183, 77, 0.15)',
        borda: '#ffb74d',
        icone: 'warning',
      };
    }
    return {
      texto: `${dias} dias até vencer`,
      corTexto: '#00e676',
      bg: 'rgba(0, 230, 118, 0.15)',
      borda: '#00e676',
      icone: 'shield-checkmark',
    };
  };

  const renderItem = ({ item }) => {
    const badge = obterBadgeInfo(item.criticidade, item.diasParaPrazoLimite);
    const custoPorGrama = Number(item.custoUnitario);

    return (
      <TouchableOpacity style={styles.card} onPress={() => setLoteHistorico(item)} accessibilityRole="button" accessibilityLabel={`Ver histórico de compras de ${item.ingredienteNome}`}>
        <View style={styles.cardHeader}>
          <View style={styles.headerLeft}>
            <Text style={styles.ingredienteNome}>{item.ingredienteNome}</Text>
            <Text style={styles.numeroLote}>Lote #{String(item.numeroLote || item.id).replace(/^#/, '')}</Text>
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
            <Text style={styles.infoValue}>{formatarData(item.dataValidade)}</Text>
          </View>
          <View style={styles.infoCol}>
            <Text style={styles.infoLabel}>Custo/g</Text>
            <Text style={styles.infoValue}>
              {Number.isFinite(custoPorGrama) ? `R$ ${custoPorGrama.toFixed(4)}` : 'Não informado'}
            </Text>
          </View>
        </View>
      </TouchableOpacity>
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
        <TouchableOpacity style={styles.retryButton} onPress={tentarNovamente} accessibilityRole="button" accessibilityLabel="Tentar carregar estoque novamente">
          <Ionicons name="refresh" size={18} color="#ffffff" />
          <Text style={styles.retryButtonText}>Tentar novamente</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.buscaLinha}><Ionicons name="search" color="#b8b8cc" size={18} /><TextInput value={busca} onChangeText={setBusca} placeholder="Buscar ingrediente..." placeholderTextColor="#8b8ba7" style={styles.buscaInput} accessibilityLabel="Buscar ingrediente" /></View>
      <View style={styles.semaforo} accessibilityLabel={`${contagens.CRITICO} críticos, ${contagens.ATENCAO} em atenção, ${contagens.SAUDAVEL} saudáveis`}>
        {[["CRITICO", "Críticos", "#ff5252"], ["ATENCAO", "Atenção", "#ffb74d"], ["SAUDAVEL", "Saudáveis", "#00e676"]].map(([tipo, rotulo, cor]) =>
          <View key={tipo} style={styles.semaforoItem}><View style={[styles.semaforoPonto, { backgroundColor: cor }]} /><Text style={styles.semaforoTexto}>{rotulo}: {contagens[tipo]}</Text></View>)}
      </View>
      <View style={styles.filterRow}>
        {['TODOS', 'CRITICO', 'ATENCAO', 'SAUDAVEL'].map((f) => (
          <TouchableOpacity
            key={f}
            style={[styles.filterChip, { borderColor: { TODOS: '#6c63ff', CRITICO: '#ff5252', ATENCAO: '#ffb74d', SAUDAVEL: '#00e676' }[f] }, filtro === f && styles.filterChipActive,
              filtro === f && { backgroundColor: { TODOS: '#6c63ff33', CRITICO: '#ff525233', ATENCAO: '#ffb74d33', SAUDAVEL: '#00e67633' }[f] }]}
            onPress={() => setFiltro(f)}
            accessibilityRole="button"
            accessibilityLabel={`Filtrar ${f === 'TODOS' ? 'todos' : f === 'CRITICO' ? 'críticos' : f === 'ATENCAO' ? 'atenção' : 'saudáveis'}`}
            accessibilityState={{ selected: filtro === f }}
          >
            <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.85} style={[styles.filterChipText, filtro === f && styles.filterChipTextActive]}>
              {f === 'TODOS' ? `Todos (${contagens.TODOS})` : f === 'CRITICO' ? 'Críticos' : f === 'ATENCAO' ? 'Atenção' : 'Saudáveis'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {avisoAtualizacao ? (
        <View style={styles.refreshWarning}>
          <Ionicons name="warning-outline" size={18} color="#ffb74d" />
          <Text style={styles.refreshWarningText}>{avisoAtualizacao}</Text>
          <TouchableOpacity onPress={onRefresh} style={styles.refreshWarningButton} accessibilityRole="button" accessibilityLabel="Repetir atualização do estoque">
            <Text style={styles.refreshWarningAction}>Repetir</Text>
          </TouchableOpacity>
        </View>
      ) : null}

      <Text style={styles.dicaHistorico}>Toque em um lote para ver o histórico de compras</Text>
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
      <HistoricoComprasModal lote={loteHistorico} onClose={() => setLoteHistorico(null)} />
    </View>
  );
}

export default function EstoqueScreen() {
  const [aba, setAba] = useState('ATUAL');
  return (
    <View style={styles.container}>
      <View style={styles.tabRow}>
        <TouchableOpacity style={[styles.tabButton, aba === 'ATUAL' && styles.tabButtonActive]} onPress={() => setAba('ATUAL')} accessibilityRole="tab" accessibilityLabel="Estoque Atual" accessibilityState={{ selected: aba === 'ATUAL' }}>
          <Text style={[styles.tabText, aba === 'ATUAL' && styles.tabTextActive]}>Estoque Atual</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.tabButton, aba === 'PREVISAO' && styles.tabButtonActive]} onPress={() => setAba('PREVISAO')} accessibilityRole="tab" accessibilityLabel="Previsão de Compras" accessibilityState={{ selected: aba === 'PREVISAO' }}>
          <Text style={[styles.tabText, aba === 'PREVISAO' && styles.tabTextActive]}>Previsão de Compras</Text>
        </TouchableOpacity>
      </View>
      {aba === 'ATUAL' ? <EstoqueAtualContent /> : <PrevisaoComprasSubScreen />}
    </View>
  );
}

const styles = StyleSheet.create({
  buscaLinha: { flexDirection: 'row', alignItems: 'center', gap: 8, margin: 12, marginBottom: 0, paddingHorizontal: 12, minHeight: 44, backgroundColor: '#1a1a33', borderWidth: 1, borderColor: '#303055', borderRadius: 12 },
  buscaInput: { flex: 1, color: '#fff', fontSize: 14 },
  dicaHistorico: { color: '#9b9bb5', fontSize: 11, textAlign: 'center', paddingTop: 8 },
  container: {
    flex: 1,
    backgroundColor: '#0f0f23',
  },
  tabRow: {
    backgroundColor: '#14142b',
    borderBottomColor: '#252545',
    borderBottomWidth: 1,
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingTop: 10,
  },
  tabButton: {
    alignItems: 'center',
    borderBottomColor: 'transparent',
    borderBottomWidth: 3,
    flex: 1,
    minHeight: 44,
    justifyContent: 'center',
    paddingBottom: 10,
  },
  tabButtonActive: {
    borderBottomColor: '#6c63ff',
  },
  tabText: {
    color: '#8b8ba7',
    fontSize: 13,
    fontWeight: '600',
  },
  tabTextActive: {
    color: '#ffffff',
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
    backgroundColor: '#5b50dc',
    borderRadius: 8,
    flexDirection: 'row',
    marginTop: 20,
    paddingHorizontal: 18,
    paddingVertical: 10,
    minHeight: 44,
  },
  retryButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: 'bold',
    marginLeft: 8,
  },
  filterRow: {
    flexDirection: 'row',
    paddingHorizontal: 8,
    paddingVertical: 12,
    backgroundColor: '#14142b',
    borderBottomWidth: 1,
    borderBottomColor: '#252545',
  },
  semaforo: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 12, paddingTop: 12, paddingBottom: 2, paddingHorizontal: 8, backgroundColor: '#14142b' },
  semaforoItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  semaforoPonto: { width: 8, height: 8, borderRadius: 4 },
  semaforoTexto: { color: '#c5c5d8', fontSize: 11 },
  filterChip: {
    flex: 1,
    alignItems: 'center',
    paddingHorizontal: 4,
    minHeight: 44,
    justifyContent: 'center',
    borderRadius: 20,
    backgroundColor: '#1f1f3a',
    marginHorizontal: 2,
    borderWidth: 1,
    borderColor: '#303055',
  },
  filterChipActive: {
    backgroundColor: '#5b50dc',
    borderColor: '#6c63ff',
  },
  filterChipText: {
    color: '#8b8ba7',
    fontSize: 12,
    textAlign: 'center',
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
  refreshWarningButton: { minHeight: 44, minWidth: 44, alignItems: 'center', justifyContent: 'center' },
  listContent: {
    padding: 16,
  },
  card: {
    backgroundColor: '#18182e',
    borderRadius: 14,
    padding: 16,
    marginBottom: 8,
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
