import React, { useCallback, useEffect, useLayoutEffect, useState } from 'react';
import {
  ActivityIndicator, Alert, RefreshControl, ScrollView, StyleSheet, Text,
  TouchableOpacity, View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { authApi, dashboardApi } from '../services/api';
import { impactoLeve, impactoSucesso } from '../services/nativeService';
import {
  formatarDataGrafico, formatarMoeda, formatarUltimaSincronizacao,
  formatarVariacao, validarPayloadDashboard,
} from './dashboardViewModel';

const mensagemErro = (error) => {
  if (error.message?.startsWith('Não foi possível conectar ao servidor.')) return error.message;
  if (error.code === 'ECONNABORTED') return 'A API demorou para responder. Verifique a rede e tente novamente.';
  if (error.message?.includes('formato inesperado')) return error.message;
  return error.response?.data?.erro || error.response?.data?.mensagem
    || (error.message && error.message !== 'Network Error' ? error.message : null)
    || 'Não foi possível carregar o Dashboard. Verifique a conexão e tente novamente.';
};

function Variacao({ valor }) {
  const variacao = formatarVariacao(valor);
  return <Text style={[styles.variacao, { color: variacao.cor }]}>{variacao.texto}</Text>;
}

function KpiCard({ icone, titulo, valor, variacao }) {
  return (
    <View style={styles.card}>
      <View style={styles.cardTituloLinha}>
        <Ionicons name={icone} size={18} color="#9e99ff" />
        <Text style={styles.cardTitulo}>{titulo}</Text>
      </View>
      <Text style={styles.cardValor} numberOfLines={1} adjustsFontSizeToFit>{valor}</Text>
      <Variacao valor={variacao} />
      <Text style={styles.comparacao}>vs. mês anterior</Text>
    </View>
  );
}

function ImpactoCard({ impacto, variacao }) {
  return (
    <View style={[styles.card, styles.impactoCard]}>
      <View style={styles.cardTituloLinha}>
        <Ionicons name="shield-checkmark-outline" size={18} color="#9e99ff" />
        <Text style={styles.cardTitulo}>Impacto</Text>
      </View>
      <Text style={styles.impactoRotulo}>Valor salvo</Text>
      <Text style={styles.valorSalvo}>{formatarMoeda(impacto.valorSalvo)}</Text>
      <View style={styles.impactoLinha}>
        <Text style={styles.valorPerdido}>Perda: {formatarMoeda(impacto.valorPerdido)}</Text>
        <Text style={styles.impactoLiquido}>Líquido: {formatarMoeda(impacto.impactoLiquido)}</Text>
      </View>
      <Variacao valor={variacao} />
    </View>
  );
}

function MiniGrafico({ titulo, dados, campo, moeda }) {
  const [selecionado, setSelecionado] = useState(dados.length ? dados.length - 1 : null);
  const maximo = Math.max(1, ...dados.map((item) => item[campo]));
  const minimo = Math.min(...dados.map((item) => Number(item[campo]) || 0));
  const itemSelecionado = selecionado == null ? null : dados[selecionado];
  return (
    <View style={styles.graficoCard}>
      <View style={styles.graficoCabecalho}>
        <Text style={styles.graficoTitulo}>{titulo}</Text>
        <Text style={styles.graficoValorExato}>
          {itemSelecionado
            ? `${new Intl.DateTimeFormat('pt-BR', { weekday: 'long' }).format(new Date(`${itemSelecionado.data}T12:00:00`)).replace(/^./, (letra) => letra.toUpperCase())}: ${moeda ? formatarMoeda(itemSelecionado[campo]) : `${itemSelecionado[campo]} pedidos`}`
            : 'Sem dados'}
        </Text>
      </View>
      <View style={styles.barras}>
        {dados.map((item, indice) => {
          const altura = maximo === minimo ? 56 : 14 + Math.round(((item[campo] - minimo) / (maximo - minimo)) * 62);
          return (
            <TouchableOpacity
              key={`${campo}-${item.data}`}
              style={styles.barraColuna}
              onPress={() => setSelecionado(indice)}
              accessibilityRole="button"
              accessibilityLabel={`${formatarDataGrafico(item.data)}, ${moeda ? formatarMoeda(item[campo]) : item[campo]}`}
            >
              <View style={styles.barraEspaco}>
                <View style={[styles.barra, indice === selecionado && styles.barraSelecionada, { height: altura }]} />
              </View>
              <Text style={styles.dia}>{formatarDataGrafico(item.data)}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
}

export default function HomeScreen({ navigation }) {
  const [dados, setDados] = useState(null);
  const [restaurante, setRestaurante] = useState('Dashboard');
  const [ultimaSincronizacao, setUltimaSincronizacao] = useState(null);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [sincronizando, setSincronizando] = useState(false);
  const [erro, setErro] = useState('');
  const [feedback, setFeedback] = useState('');

  useLayoutEffect(() => {
    navigation?.setOptions({ headerTitle: restaurante, tabBarLabel: 'Painel' });
  }, [navigation, restaurante]);

  const carregar = useCallback(async ({ preservar = false, avisar = false } = {}) => {
    setErro('');
    try {
      const [kpisResult, perfilResult, statusResult] = await Promise.allSettled([
        dashboardApi.getKpis(), authApi.getMe(), dashboardApi.getStatusPdv(),
      ]);
      if (kpisResult.status === 'rejected') throw kpisResult.reason;
      setDados(validarPayloadDashboard(kpisResult.value));
      if (perfilResult.status === 'fulfilled' && perfilResult.value?.restauranteNome) {
        setRestaurante(perfilResult.value.restauranteNome);
      }
      if (statusResult.status === 'fulfilled') {
        setUltimaSincronizacao(statusResult.value?.ultimaSincronizacao || null);
      }
    } catch (error) {
      if (!preservar) setDados(null);
      const texto = mensagemErro(error);
      setErro(texto);
      if (avisar) Alert.alert('Não foi possível atualizar', texto);
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useEffect(() => { carregar(); }, [carregar]);

  const atualizar = () => {
    setAtualizando(true);
    setFeedback('');
    carregar({ preservar: true, avisar: true });
  };

  const sincronizar = async () => {
    if (sincronizando) return;
    void impactoLeve();
    setSincronizando(true);
    setFeedback('');
    try {
      const resposta = await dashboardApi.sincronizarPdv();
      if (resposta?.sucesso === false) throw new Error(resposta.mensagem || 'A sincronização não foi concluída.');
      if (resposta?.ultimaSincronizacao) setUltimaSincronizacao(resposta.ultimaSincronizacao);
      await carregar({ preservar: true });
      setFeedback('Sincronização concluída');
      void impactoSucesso();
    } catch (error) {
      Alert.alert('Não foi possível sincronizar', mensagemErro(error));
    } finally {
      setSincronizando(false);
    }
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.conteudo}
      refreshControl={<RefreshControl refreshing={atualizando} onRefresh={atualizar} tintColor="#8f89ff" />}
    >
      <View style={styles.periodoLinha}>
        <View>
          <Text style={styles.saudacao}>Visão Geral</Text>
          <Text style={styles.restaurante}>{restaurante === 'Dashboard' ? 'Restaurante ativo' : restaurante}</Text>
        </View>
        <View style={styles.periodoPill}><Text style={styles.periodoTexto}>Este mês</Text></View>
      </View>

      {erro ? <Text accessibilityRole="alert" style={styles.aviso}>{erro}</Text> : null}
      {carregando && !dados ? (
        <View style={styles.centro}>
          <ActivityIndicator color="#8f89ff" size="large" />
          <Text style={styles.detalhe}>Carregando indicadores...</Text>
        </View>
      ) : dados ? (
        <>
          <View style={styles.grade}>
            <KpiCard icone="cash-outline" titulo="Faturamento" valor={formatarMoeda(dados.faturamentoTotal)} variacao={dados.variacoes.faturamentoPct} />
            <KpiCard icone="receipt-outline" titulo="Total de pedidos" valor={String(dados.totalPedidos)} variacao={dados.variacoes.totalPedidosPct} />
            <KpiCard icone="ticket-outline" titulo="Ticket médio" valor={formatarMoeda(dados.ticketMedio)} variacao={dados.variacoes.ticketMedioPct} />
            <ImpactoCard impacto={dados.impactoFinanceiro} variacao={dados.variacoes.impactoLiquidoPct} />
          </View>

          <Text style={styles.secaoTitulo}>Últimos 7 dias</Text>
          <MiniGrafico titulo="Pedidos por dia" dados={dados.ultimosSeteDias} campo="quantidadePedidos" />
          <MiniGrafico titulo="Faturamento por dia" dados={dados.ultimosSeteDias} campo="totalVendas" moeda />
        </>
      ) : null}

      <View style={styles.syncCard}>
        <View style={styles.syncInfo}>
          <Text style={styles.syncRotulo}>Última sincronização com sucesso</Text>
          <Text style={styles.syncData}>{formatarUltimaSincronizacao(ultimaSincronizacao)}</Text>
          {feedback ? <Text accessibilityRole="alert" style={styles.sucesso}>{feedback}</Text> : null}
        </View>
        <TouchableOpacity
          style={[styles.syncBotao, sincronizando && styles.syncBotaoDesabilitado]}
          onPress={sincronizar}
          disabled={sincronizando}
          accessibilityRole="button"
          accessibilityLabel="Sincronizar vendas do PDV"
        >
          {sincronizando ? <ActivityIndicator color="#fff" size="small" /> : <Ionicons name="sync" size={19} color="#fff" />}
          <Text style={styles.syncBotaoTexto}>{sincronizando ? 'Sincronizando...' : 'Sincronizar'}</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  conteudo: { padding: 16, paddingBottom: 36 },
  periodoLinha: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  saudacao: { color: '#fff', fontSize: 22, fontWeight: 'bold' },
  restaurante: { color: '#9b9bb5', fontSize: 13, marginTop: 4 },
  periodoPill: { backgroundColor: '#292650', borderColor: '#514b92', borderWidth: 1, borderRadius: 18, paddingHorizontal: 13, paddingVertical: 8 },
  periodoTexto: { color: '#d7d4ff', fontWeight: '600', fontSize: 12 },
  aviso: { color: '#ffcf82', backgroundColor: '#493822', padding: 11, borderRadius: 10, marginBottom: 12, fontSize: 13 },
  centro: { minHeight: 260, alignItems: 'center', justifyContent: 'center' },
  detalhe: { color: '#aaaac1', fontSize: 13, marginTop: 10 },
  grade: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 10 },
  card: { width: '48.4%', minHeight: 156, backgroundColor: '#1a1a33', borderColor: '#303055', borderWidth: 1, borderRadius: 15, padding: 14 },
  impactoCard: { borderColor: '#3a4260' },
  cardTituloLinha: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  cardTitulo: { color: '#b7b7cd', fontSize: 11, fontWeight: '600', flex: 1, textAlign: 'center', marginRight: 18 },
  cardValor: { color: '#fff', fontSize: 23, fontWeight: 'bold', marginTop: 17 },
  variacao: { fontSize: 12, fontWeight: 'bold', marginTop: 13 },
  comparacao: { color: '#aaaac1', fontSize: 11, marginTop: 3 },
  impactoRotulo: { color: '#aaaac1', fontSize: 11, marginTop: 9 },
  valorSalvo: { color: '#69db7c', fontSize: 19, fontWeight: 'bold', marginTop: 2 },
  impactoLinha: { marginTop: 7, gap: 2 },
  valorPerdido: { color: '#ef8a91', fontSize: 10, fontWeight: '600' },
  impactoLiquido: { color: '#c6c6da', fontSize: 9 },
  secaoTitulo: { color: '#fff', fontSize: 18, fontWeight: 'bold', marginTop: 25, marginBottom: 10 },
  graficoCard: { backgroundColor: '#18182f', borderColor: '#2d2d50', borderWidth: 1, borderRadius: 15, padding: 14, marginBottom: 12 },
  graficoCabecalho: { alignItems: 'center', gap: 7 },
  graficoTitulo: { color: '#ededf7', fontSize: 13, fontWeight: 'bold' },
  graficoValorExato: { color: '#d7d4ff', fontSize: 16, fontWeight: '700', textAlign: 'center' },
  barras: { height: 112, flexDirection: 'row', alignItems: 'flex-end', marginTop: 10 },
  barraColuna: { flex: 1, height: 104, alignItems: 'center', justifyContent: 'flex-end' },
  barraEspaco: { flex: 1, justifyContent: 'flex-end', alignItems: 'center' },
  barra: { width: 13, minHeight: 4, borderRadius: 5, backgroundColor: '#5f5a99' },
  barraSelecionada: { backgroundColor: '#00e676' },
  dia: { color: '#aaaac1', fontSize: 11, marginTop: 7 },
  syncCard: { backgroundColor: '#1a1a33', borderColor: '#34345a', borderWidth: 1, borderRadius: 15, padding: 14, marginTop: 8, flexDirection: 'row', alignItems: 'center', gap: 12 },
  syncInfo: { flex: 1 },
  syncRotulo: { color: '#aaaac1', fontSize: 11 },
  syncData: { color: '#e3e3ee', fontSize: 12, fontWeight: '600', marginTop: 4 },
  sucesso: { color: '#69db7c', fontSize: 11, marginTop: 5 },
  syncBotao: { minWidth: 112, minHeight: 44, borderRadius: 11, backgroundColor: '#5b50dc', flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 7, paddingHorizontal: 12 },
  syncBotaoDesabilitado: { opacity: 0.7 },
  syncBotaoTexto: { color: '#fff', fontSize: 11, fontWeight: 'bold' },
});
