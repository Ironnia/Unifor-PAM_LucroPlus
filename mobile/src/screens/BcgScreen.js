import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator, Alert, RefreshControl, SectionList, StyleSheet, Text,
  TouchableOpacity, View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { cardapioApi } from '../services/api';
import { formatarMoeda } from './rankingViewModel';
import {
  agruparPorQuadrante, pluralizarPratos, validarBcg,
} from './bcgViewModel';

const mensagemErro = (error) => {
  if (error.message?.startsWith('Não foi possível conectar ao servidor.')) return error.message;
  if (error.code === 'ECONNABORTED') return 'A API demorou para responder. Verifique a rede e tente novamente.';
  if (error.message?.includes('formato inesperado')) return error.message;
  return error.response?.data?.erro || 'Não foi possível carregar a Matriz BCG. Verifique a conexão e tente novamente.';
};

export default function BcgScreen() {
  const listaRef = useRef(null);
  const secaoDestinoRef = useRef(0);
  const [resposta, setResposta] = useState(null);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState('');
  const [pratoSelecionado, setPratoSelecionado] = useState(null);
  const [explicacaoAberta, setExplicacaoAberta] = useState(false);
  const [desempenhos, setDesempenhos] = useState({});

  const secoes = useMemo(() => agruparPorQuadrante(resposta?.pratos || []), [resposta]);

  const carregarBcg = useCallback(async ({ preservar = false, avisar = false } = {}) => {
    setErro('');
    try {
      const [bcg, rankingResultado] = await Promise.all([cardapioApi.getBcg(), cardapioApi.getRanking().then((dados) => dados, () => [])]);
      setResposta(validarBcg(bcg));
      setDesempenhos(Object.fromEntries((Array.isArray(rankingResultado) ? rankingResultado : []).map((item) => [item.produtoId, item.desempenho6Meses || []])));
    } catch (error) {
      if (!preservar) setResposta(null);
      const texto = mensagemErro(error);
      setErro(texto);
      if (avisar) Alert.alert('Não foi possível atualizar', texto);
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useEffect(() => { carregarBcg(); }, [carregarBcg]);

  const atualizar = () => {
    setAtualizando(true);
    carregarBcg({ preservar: true, avisar: true });
  };

  const rolarParaQuadrante = (sectionIndex) => {
    secaoDestinoRef.current = sectionIndex;
    listaRef.current?.scrollToLocation({
      animated: true,
      sectionIndex,
      itemIndex: 0,
      viewOffset: 8,
    });
  };

  const renderCabecalho = () => (
    <View>
      <View style={styles.introducao}>
        <Text style={styles.subtitulo}>Classificação dos pratos neste mês. Toque em um quadrante para ver os pratos.</Text>
      </View>

      <View style={styles.grid}>
        {secoes.map((secao, index) => (
          <TouchableOpacity
            key={secao.codigo}
            style={[styles.quadrante, { borderColor: secao.cor }]}
            onPress={() => rolarParaQuadrante(index)}
            accessibilityRole="button"
            accessibilityLabel={`${secao.titulo}: ${pluralizarPratos(secao.quantidade)}. Ir para a seção.`}
          >
            <View style={styles.quadranteLinha}><Text style={styles.quadranteIcone}>{secao.icone}</Text><Text style={[styles.quadranteQuantidade, { color: secao.cor }]}>{secao.quantidade}</Text></View>
            <Text style={styles.quadranteTitulo}>{secao.titulo}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <View style={styles.explicacao}>
        <TouchableOpacity style={styles.explicacaoCabecalho} onPress={() => setExplicacaoAberta(!explicacaoAberta)} accessibilityRole="button" accessibilityState={{ expanded: explicacaoAberta }}>
          <Ionicons name="information-circle-outline" size={22} color="#9e98ff" />
          <Text style={styles.explicacaoTitulo}>Entenda a Classificação BCG</Text>
          <Ionicons name={explicacaoAberta ? 'chevron-up' : 'chevron-down'} size={18} color="#9e98ff" />
        </TouchableOpacity>
        {explicacaoAberta ? <><Text style={styles.explicacaoApoio}>Alta ou baixa significa comparação com a média dos pratos ativos neste mês.</Text>
        {secoes.map((secao) => (
          <View key={secao.codigo} style={styles.recomendacaoLinha}>
            <Text style={styles.recomendacaoIcone}>{secao.icone}</Text>
            <View style={styles.recomendacaoTexto}>
              <Text style={styles.recomendacaoTitulo}>{secao.titulo}: {secao.combinacao}</Text>
              <Text style={styles.recomendacaoDetalhe}>{secao.recomendacao}</Text>
            </View>
          </View>
        ))}</> : null}
      </View>
      {erro ? <Text accessibilityRole="alert" style={styles.aviso}>{erro}</Text> : null}
    </View>
  );

  const renderPrato = ({ item }) => item.vazio ? (
    <View style={styles.cardVazio}>
      <Text style={styles.secaoVazia}>Nenhum prato nesta categoria.</Text>
    </View>
  ) : (
    <TouchableOpacity
      style={styles.card}
      onPress={() => setPratoSelecionado(item)}
      accessibilityRole="button"
      accessibilityLabel={`Abrir evolução BCG de ${item.nome}`}
    >
      <View style={styles.cardTopo}><Text style={styles.nome}>{item.nome}</Text><Text style={styles.metrica}>{formatarMoeda(item.preco)}</Text></View>
    </TouchableOpacity>
  );

  if (carregando) {
    return (
      <View style={styles.centro}>
        <ActivityIndicator color="#8f89ff" size="large" />
        <Text style={styles.subtitulo}>Carregando Matriz BCG...</Text>
      </View>
    );
  }

  if (!resposta) {
    return (
      <View style={styles.centro}>
        <Ionicons name="cloud-offline-outline" size={45} color="#ef9a9a" />
        <Text accessibilityRole="alert" style={styles.erroTitulo}>Não foi possível abrir a matriz</Text>
        <Text style={styles.subtitulo}>{erro}</Text>
        <TouchableOpacity style={styles.tentarBotao} onPress={() => { setCarregando(true); carregarBcg(); }} accessibilityRole="button" accessibilityLabel="Tentar carregar Matriz BCG novamente">
          <Text style={styles.tentarTexto}>Tentar novamente</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <SectionList
        ref={listaRef}
        sections={secoes}
        keyExtractor={(item) => String(item.produtoId)}
        renderItem={renderPrato}
        renderSectionHeader={({ section }) => (
          <View style={styles.secaoCabecalho}>
            <Text style={styles.secaoTitulo}>{section.icone} {section.titulo} ({section.quantidade})</Text>
          </View>
        )}
        ListHeaderComponent={renderCabecalho}
        contentContainerStyle={styles.lista}
        stickySectionHeadersEnabled={false}
        refreshControl={<RefreshControl refreshing={atualizando} onRefresh={atualizar} tintColor="#8f89ff" />}
        onScrollToIndexFailed={() => setTimeout(() => listaRef.current?.scrollToLocation({
          animated: true,
          sectionIndex: secaoDestinoRef.current,
          itemIndex: 0,
          viewOffset: 8,
        }), 120)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  lista: { padding: 14, paddingBottom: 30 },
  introducao: { marginBottom: 12 },
  titulo: { color: '#fff', fontSize: 19, fontWeight: 'bold' },
  subtitulo: { color: '#b8b8cc', marginTop: 6, fontSize: 13, lineHeight: 19, textAlign: 'center' },
  medias: { color: '#9292ad', fontSize: 11, lineHeight: 17, marginTop: 7 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 10 },
  quadrante: { width: '48%', minHeight: 84, backgroundColor: '#1a1a33', borderWidth: 1, borderRadius: 14, padding: 13 },
  quadranteLinha: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  quadranteIcone: { fontSize: 25 },
  quadranteTitulo: { color: '#fff', fontSize: 14, fontWeight: 'bold', marginTop: 8 },
  quadranteQuantidade: { fontSize: 25, fontWeight: 'bold', marginTop: 0 },
  explicacao: { backgroundColor: '#1d1d39', borderColor: '#34345a', borderWidth: 1, borderRadius: 14, padding: 15, marginTop: 14, marginBottom: 14 },
  explicacaoCabecalho: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  explicacaoTitulo: { color: '#fff', fontSize: 16, fontWeight: 'bold', flex: 1 },
  explicacaoApoio: { color: '#a8a8bd', fontSize: 12, lineHeight: 17, marginTop: 7, marginBottom: 4 },
  recomendacaoLinha: { flexDirection: 'row', marginTop: 11 },
  recomendacaoIcone: { fontSize: 17, width: 28 },
  recomendacaoTexto: { flex: 1 },
  recomendacaoTitulo: { color: '#e4e4f1', fontSize: 12, fontWeight: 'bold' },
  recomendacaoDetalhe: { color: '#aaaac0', fontSize: 12, lineHeight: 17, marginTop: 2 },
  aviso: { color: '#ffcf82', backgroundColor: '#493822', borderRadius: 9, padding: 11, fontSize: 13, marginBottom: 10 },
  secaoCabecalho: { backgroundColor: '#0f0f23', paddingTop: 17, paddingBottom: 8 },
  secaoTitulo: { color: '#fff', fontSize: 18, fontWeight: 'bold' },
  secaoQuantidade: { fontSize: 11, fontWeight: 'bold', marginTop: 3 },
  secaoVazia: { color: '#8585a0', fontSize: 12, marginTop: 8, fontStyle: 'italic' },
  card: { backgroundColor: '#1a1a33', borderColor: '#303055', borderWidth: 1, borderRadius: 13, padding: 15, marginBottom: 9 },
  cardVazio: { backgroundColor: '#17172d', borderColor: '#292947', borderWidth: 1, borderStyle: 'dashed', borderRadius: 12, padding: 14, marginBottom: 9 },
  cardTopo: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  nome: { color: '#fff', fontSize: 16, fontWeight: 'bold', flex: 1 },
  metricas: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 6, marginTop: 9 },
  metrica: { color: '#d7d7e8', fontSize: 12 },
  separador: { color: '#6f6f8b' },
  lucro: { color: '#a8d5ad', fontSize: 11, marginTop: 8 },
  centro: { flex: 1, minHeight: 320, alignItems: 'center', justifyContent: 'center', padding: 30 },
  erroTitulo: { color: '#fff', fontWeight: 'bold', fontSize: 17, marginTop: 12, textAlign: 'center' },
  tentarBotao: { backgroundColor: '#5b50dc', borderRadius: 10, paddingHorizontal: 18, paddingVertical: 11, marginTop: 17, minHeight: 44, justifyContent: 'center' },
  tentarTexto: { color: '#fff', fontSize: 13, fontWeight: 'bold' },
});
