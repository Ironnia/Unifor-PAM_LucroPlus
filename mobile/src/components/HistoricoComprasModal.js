import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Animated, Modal, PanResponder, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { lotesApi } from '../services/api';

const numero = (valor, casas = 1) => new Intl.NumberFormat('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas }).format(Number(valor) || 0);
const moeda = (valor) => `R$ ${numero(valor, 2)}`;

export default function HistoricoComprasModal({ lote, onClose }) {
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState('');
  const deslocamento = useRef(new Animated.Value(0)).current;
  const fechar = useRef(onClose);
  fechar.current = onClose;
  const gesto = useRef(PanResponder.create({
    onMoveShouldSetPanResponder: (_, movimento) => movimento.dy > 8,
    onPanResponderMove: (_, movimento) => deslocamento.setValue(Math.max(0, movimento.dy)),
    onPanResponderRelease: (_, movimento) => {
      if (movimento.dy > 75 || movimento.vy > 0.8) fechar.current();
      Animated.spring(deslocamento, { toValue: 0, useNativeDriver: true }).start();
    },
  })).current;
  useEffect(() => {
    if (!lote) return undefined;
    let ativo = true;
    setDados(null);
    setErro('');
    lotesApi.getHistorico(lote.ingredienteId).then((resposta) => {
      if (ativo) setDados(resposta);
    }).catch(() => {
      if (ativo) setErro('Não foi possível carregar o histórico de compras.');
    });
    return () => { ativo = false; };
  }, [lote?.ingredienteId]);

  const compras = [...(dados?.compras || [])].sort((a, b) => b.dataEntrada.localeCompare(a.dataEntrada));
  const unidade = dados?.unidade === 'un' ? 'un' : 'kg';
  const meses = [...compras.reduce((grupos, compra) => {
    const atual = grupos.get(compra.mesReferencia) || { mes: compra.mesReferencia, total: 0, quantidade: 0 };
    const quantidade = Number(compra.quantidadeCompradaG) || 0;
    atual.total += (Number(compra.precoPorKg) || 0) * quantidade;
    atual.quantidade += quantidade;
    grupos.set(compra.mesReferencia, atual);
    return grupos;
  }, new Map()).values()].slice(0, 6).map((grupo) => ({ mes: grupo.mes, preco: grupo.quantidade ? grupo.total / grupo.quantidade : 0 }));
  const compraUnidade = compras.find((compra) => /un$/.test(compra.quantidadeFormatada || ''));
  const quantidadeUnidades = Number(compraUnidade?.quantidadeFormatada?.replace(' un', '').replace(',', '.'));
  const fatorUnidade = quantidadeUnidades > 0 ? compraUnidade.quantidadeCompradaG / quantidadeUnidades : null;
  const consumo = unidade === 'un' ? (fatorUnidade ? numero(dados?.consumoMedioMensalG / fatorUnidade) : '—') : numero(dados?.consumoMedioMensalG / 1000);
  const maior = Math.max(1, ...meses.map((mes) => mes.preco));
  return <Modal visible={Boolean(lote)} transparent animationType="slide" onRequestClose={onClose}>
    <View style={styles.fundo}>
      <Pressable style={styles.fora} onPress={onClose} accessibilityLabel="Fechar histórico de compras" />
      <Animated.View style={[styles.folha, { transform: [{ translateY: deslocamento }] }]}>
        <View style={styles.cabecalho} {...gesto.panHandlers}>
          <View style={styles.puxador} />
          <View style={{ flex: 1 }}><Text style={styles.titulo}>{dados?.ingredienteNome || lote?.ingredienteNome}</Text>
            {dados ? <Text style={styles.consumo}>Consumo médio: {consumo} {unidade} / mês</Text> : null}</View>
          <Pressable onPress={onClose} accessibilityRole="button" accessibilityLabel="Fechar"><Ionicons name="close" size={24} color="#fff" /></Pressable>
        </View>
        {!dados && !erro ? <ActivityIndicator color="#8f89ff" style={{ margin: 30 }} /> : null}
        {erro ? <Text accessibilityRole="alert" style={styles.erro}>{erro}</Text> : null}
        {dados ? <ScrollView contentContainerStyle={styles.conteudo}>
          <Text style={styles.secao}>Preço de compra · R$/{unidade}</Text>
          {meses.length ? <View style={styles.grafico}>
            {meses.map((mes) => <View key={mes.mes} style={styles.coluna}>
              <Text style={styles.graficoValor} numberOfLines={1} adjustsFontSizeToFit>{moeda(mes.preco)}</Text>
              <View style={[styles.barra, { height: Math.max(5, 76 * mes.preco / maior) }]} />
              <Text style={styles.graficoMes}>{mes.mes}</Text>
            </View>)}
          </View> : <Text style={styles.vazio}>Nenhuma compra registrada.</Text>}
          <Text style={styles.secao}>Últimas compras (6 meses)</Text>
          <View style={styles.linhaTabela}><Text style={styles.celula}>Mês/Ano</Text><Text style={styles.celula}>Qtd comprada</Text><Text style={styles.celula}>R$/{unidade}</Text><Text style={styles.celula}>Saldo</Text></View>
          {compras.map((compra) => <View key={compra.loteId} style={styles.compra}>
            <View style={styles.linhaTabela}><Text style={styles.celula}>{compra.mesReferencia} #{String(compra.numeroLote).replace(/^#/, '')}</Text><Text style={styles.celula}>{compra.quantidadeFormatada}</Text><Text style={styles.celula}>{moeda(compra.precoPorKg)}</Text><Text style={[styles.celula, { color: compra.emEstoque ? '#00e676' : '#b8b8cc' }]}>{compra.emEstoque ? '●' : '○'} {compra.quantidadeRestanteG == null ? (compra.emEstoque ? 'Em estoque' : '0 g') : unidade === 'un' && fatorUnidade ? `${numero(compra.quantidadeRestanteG / fatorUnidade)} un` : compra.quantidadeRestanteG >= 1000 ? `${numero(compra.quantidadeRestanteG / 1000)} kg` : `${compra.quantidadeRestanteG} g`}</Text></View>
          </View>)}
        </ScrollView> : null}
      </Animated.View>
    </View>
  </Modal>;
}

const styles = StyleSheet.create({
  fundo: { flex: 1, justifyContent: 'flex-end', backgroundColor: '#000a' }, fora: { flex: 1 },
  folha: { maxHeight: '85%', backgroundColor: '#17172f', borderTopLeftRadius: 20, borderTopRightRadius: 20, padding: 18 },
  puxador: { width: 42, height: 4, borderRadius: 2, backgroundColor: '#696985', alignSelf: 'center', position: 'absolute', top: -9 },
  cabecalho: { flexDirection: 'row', alignItems: 'center', gap: 12 }, titulo: { color: '#fff', fontSize: 20, fontWeight: 'bold' },
  consumo: { color: '#b8b8cc', fontSize: 13, marginTop: 5 }, conteudo: { paddingBottom: 30 },
  secao: { color: '#fff', fontWeight: 'bold', marginTop: 22, marginBottom: 10 },
  grafico: { height: 130, flexDirection: 'row', alignItems: 'flex-end', gap: 5, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#383858' },
  coluna: { flex: 1, alignItems: 'center', justifyContent: 'flex-end' },
  barra: { width: '75%', backgroundColor: '#00e676', borderTopLeftRadius: 4, borderTopRightRadius: 4 },
  graficoValor: { color: '#d7d7e8', fontSize: 9, marginBottom: 4, textAlign: 'center' },
  graficoMes: { color: '#b8b8cc', fontSize: 9, marginTop: 5 },
  linhaTabela: { flexDirection: 'row', gap: 3, marginTop: 10 }, celula: { flex: 1, color: '#d7d7e8', fontSize: 11 },
  compra: { borderBottomWidth: 1, borderBottomColor: '#30304c', paddingBottom: 9 },
  status: { fontSize: 11, marginTop: 5 }, erro: { color: '#ffb74d', marginVertical: 20 }, vazio: { color: '#b8b8cc' },
});
