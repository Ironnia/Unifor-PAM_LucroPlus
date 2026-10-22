import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator, Alert, FlatList, RefreshControl, StyleSheet, Text,
  TouchableOpacity, View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { cardapioApi } from '../services/api';
import {
  formatarMoeda, formatarPercentual, obterPodio, obterSaudeMargem,
  obterVariacao, validarRanking,
} from './rankingViewModel';

const mensagemErro = (error) => {
  if (error.message?.startsWith('Não foi possível conectar ao servidor.')) return error.message;
  if (error.code === 'ECONNABORTED') return 'A API demorou para responder. Verifique a rede e tente novamente.';
  if (error.message?.includes('formato inesperado')) return error.message;
  return error.response?.data?.erro || 'Não foi possível carregar o ranking. Verifique a conexão e tente novamente.';
};

export default function RankingScreen() {
  const [ranking, setRanking] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState('');

  const carregarRanking = useCallback(async ({ preservar = false, avisar = false } = {}) => {
    setErro('');
    try {
      setRanking(validarRanking(await cardapioApi.getRanking()));
    } catch (error) {
      if (!preservar) setRanking([]);
      const texto = mensagemErro(error);
      setErro(texto);
      if (avisar) Alert.alert('Não foi possível atualizar', texto);
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useEffect(() => { carregarRanking(); }, [carregarRanking]);

  const atualizar = () => {
    setAtualizando(true);
    carregarRanking({ preservar: true, avisar: true });
  };

  const renderPrato = ({ item }) => {
    const podio = obterPodio(item.posicao);
    const variacao = obterVariacao(item.variacaoPosicao);
    const saude = obterSaudeMargem(item.margemPct);
    return (
      <TouchableOpacity
        style={[styles.card, podio && { borderColor: podio.cor, borderWidth: 2 }]}
        accessibilityRole="button"
        accessibilityLabel={`Posição ${item.posicao}, ${item.nome}`}
      >
        <Text style={styles.nome}>{podio?.medalha || '•'} {item.posicao}º • {item.nome}</Text>
        <View style={styles.linhaCompacta}>
          <Text style={styles.lucro}>{formatarMoeda(item.lucroBrutoTotal)}</Text>
          <Text style={styles.detalhe}>Margem: {formatarPercentual(item.margemPct)}</Text>
        </View>
        <View style={styles.linhaCompacta}>
          <Text style={[styles.variacaoTexto, { color: variacao.cor }]}>{item.variacaoPosicao == null ? '— Sem comparativo MTD' : item.variacaoPosicao === 0 ? '▬ Manteve' : `${variacao.simbolo} ${item.variacaoPosicao > 0 ? '+' : ''}${item.variacaoPosicao} posições`}</Text>
          <View style={[styles.badge, { borderColor: saude.cor, backgroundColor: `${saude.cor}22` }]}>
            <View style={[styles.ponto, { backgroundColor: saude.cor }]} />
            <Text style={[styles.badgeTexto, { color: saude.cor }]}>{saude.rotulo}</Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={styles.container}>
      <View style={styles.explicacao}>
        <Text style={styles.detalhe}>Ordenado pelo lucro bruto estimado em R$ gerado no mês.</Text>
      </View>
      {erro ? <Text accessibilityRole="alert" style={styles.aviso}>{erro}</Text> : null}
      {carregando ? (
        <View style={styles.centro}><ActivityIndicator color="#8f89ff" size="large" /><Text style={styles.detalhe}>Carregando ranking...</Text></View>
      ) : (
        <FlatList
          data={ranking}
          keyExtractor={(item) => String(item.produtoId)}
          renderItem={renderPrato}
          contentContainerStyle={styles.lista}
          refreshControl={<RefreshControl refreshing={atualizando} onRefresh={atualizar} tintColor="#8f89ff" />}
          ListEmptyComponent={<View style={styles.centro}><Ionicons name="restaurant-outline" size={44} color="#8b8ba7" /><Text style={styles.tituloVazio}>Ranking ainda vazio</Text><Text style={styles.detalhe}>Os pratos aparecerão após existirem vendas no período.</Text></View>}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  abas: { flexDirection: 'row', backgroundColor: '#17172f', borderBottomWidth: 1, borderBottomColor: '#303055' },
  aba: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  abaAtiva: { borderBottomWidth: 2, borderBottomColor: '#8f89ff' },
  abaTexto: { color: '#fff', fontWeight: '600', fontSize: 13 },
  explicacao: { paddingHorizontal: 16, paddingTop: 15, paddingBottom: 10 },
  explicacaoTitulo: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
  lista: { padding: 14, flexGrow: 1 },
  card: { backgroundColor: '#1a1a33', borderColor: '#303055', borderWidth: 1, borderRadius: 14, padding: 16, marginBottom: 12 },
  cardCabecalho: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 10 },
  posicaoBloco: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  medalha: { color: '#fff', fontSize: 22, fontWeight: 'bold' },
  posicao: { color: '#d8d8ed', fontSize: 12, fontWeight: 'bold', textTransform: 'uppercase' },
  badge: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderRadius: 12, paddingHorizontal: 8, paddingVertical: 4, gap: 5 },
  ponto: { width: 7, height: 7, borderRadius: 4 },
  badgeTexto: { fontSize: 10, fontWeight: 'bold' },
  nome: { color: '#fff', fontSize: 16, fontWeight: 'bold' },
  lucro: { color: '#c7f9cc', fontSize: 17, fontWeight: 'bold' },
  linhaCompacta: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginTop: 10 },
  categoria: { fontSize: 12, fontWeight: 'bold', flexShrink: 1, textAlign: 'right' },
  lucroLegenda: { color: '#8f8faa', fontSize: 11, marginTop: 2 },
  metricas: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', marginTop: 13, gap: 6 },
  metrica: { color: '#d7d7e8', fontSize: 13 },
  separador: { color: '#6f6f8b' },
  variacaoLinha: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', marginTop: 15, gap: 6 },
  variacaoSimbolo: { fontSize: 15, fontWeight: 'bold' },
  variacaoTexto: { fontSize: 12, fontWeight: 'bold' },
  comparacao: { color: '#aaaac1', fontSize: 11 },
  centro: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 30 },
  tituloVazio: { color: '#fff', fontWeight: 'bold', fontSize: 17, marginTop: 12, textAlign: 'center' },
  detalhe: { color: '#b8b8cc', marginTop: 6, fontSize: 13, lineHeight: 19, textAlign: 'center' },
  aviso: { color: '#ffcf82', backgroundColor: '#493822', padding: 11, fontSize: 13 },
});
