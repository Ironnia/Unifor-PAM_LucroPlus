import React, { useRef } from 'react';
import {
  Animated, Modal, PanResponder, Pressable, ScrollView, StyleSheet, Text, View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { formatarMoeda, formatarPercentual } from '../screens/rankingViewModel';
import { obterCategoria } from '../screens/bcgViewModel';

function formatarMes(mesReferencia) {
  const [ano, mes] = mesReferencia.split('-').map(Number);
  const meses = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];
  return ano && mes >= 1 && mes <= 12 ? `${meses[mes - 1]}/${String(ano).slice(-2)}` : mesReferencia;
}

export default function BcgEvolutionModal({ prato, visible, onClose }) {
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
  if (!prato) return null;
  const categoriaAtual = obterCategoria(prato.categoriaBcg);
  const historico = Array.isArray(prato.historicoBcg) ? [...prato.historicoBcg].sort((a, b) => b.mesReferencia.localeCompare(a.mesReferencia)) : [];
  const desempenho = Array.isArray(prato.desempenho6Meses) ? [...prato.desempenho6Meses].sort((a, b) => b.mes.localeCompare(a.mes)) : [];
  const maiorLucro = Math.max(1, ...desempenho.map((mes) => Number(mes.lucroBrutoTotal) || 0));
  const vendas = prato.vendas30Dias ?? prato.vendasUnidades;
  const lucroUnitario = Number(prato.preco) - Number(prato.cmv);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.fundo}>
        <Pressable style={styles.areaFechar} onPress={onClose} accessibilityLabel="Fechar evolução BCG" />
        <Animated.View style={[styles.folha, { transform: [{ translateY: deslocamento }] }]}>
          <View style={styles.puxador} {...gesto.panHandlers} />
          <View style={styles.cabecalho} {...gesto.panHandlers}>
            <View style={styles.cabecalhoTexto}>
              <Text style={styles.sobretitulo}>Evolução BCG</Text>
              <Text style={styles.titulo}>{prato.nome}</Text>
            </View>
            <Pressable onPress={onClose} hitSlop={10} accessibilityRole="button" accessibilityLabel="Fechar modal">
              <Ionicons name="close" size={25} color="#d9d9ea" />
            </Pressable>
          </View>

          <ScrollView contentContainerStyle={styles.conteudo}>
            <View style={[styles.atual, { borderColor: categoriaAtual?.cor || '#55556f' }]}>
              <Text style={styles.categoriaAtual}>
                {categoriaAtual ? `${categoriaAtual.icone} ${categoriaAtual.singular}` : 'Sem categoria atual'}
              </Text>
              {categoriaAtual ? <Text style={styles.explicacao}>{categoriaAtual.combinacao}</Text> : null}
            </View>

            <View style={styles.resumo}>
              <View style={styles.metrica}><Text style={styles.rotulo}>Preço venda</Text><Text style={styles.valor}>{formatarMoeda(prato.preco)}</Text></View>
              <View style={styles.metrica}><Text style={styles.rotulo}>Lucro / un.</Text><Text style={styles.valor}>{Number.isFinite(lucroUnitario) ? formatarMoeda(lucroUnitario) : '—'}</Text></View>
              <View style={styles.metrica}><Text style={styles.rotulo}>Margem</Text><Text style={styles.valor}>{formatarPercentual(prato.margemPct)}</Text></View>
              <View style={styles.metrica}><Text style={styles.rotulo}>Vendas mês</Text><Text style={styles.valor}>{Number.isInteger(vendas) ? `${vendas} un` : '—'}</Text></View>
            </View>

            <Text style={styles.historicoTitulo}>Desempenho dos últimos 6 meses</Text>
            {desempenho.length ? <View style={styles.grafico}>
              {desempenho.map((mes) => <View key={mes.mes} style={styles.coluna}>
                <Text style={styles.graficoValor}>{formatarMoeda(mes.lucroBrutoTotal)}</Text>
                <View style={[styles.barra, { height: Math.max(5, 76 * (Number(mes.lucroBrutoTotal) || 0) / maiorLucro) }]} />
                <Text style={styles.graficoMes}>{formatarMes(mes.mes)}</Text>
              </View>)}
            </View> : <Text style={styles.semHistoricoTexto}>Ainda não há vendas mensais para este prato.</Text>}

            <Text style={styles.historicoTitulo}>Meses fechados</Text>
            {historico.length ? historico.map((registro) => {
              const categoria = obterCategoria(registro.categoria);
              return (
                <View key={registro.mesReferencia} style={styles.registro}>
                  <Text style={styles.mes}>{formatarMes(registro.mesReferencia)}</Text>
                  <Text style={[styles.categoriaHistorica, { color: categoria?.cor || '#c7c7db' }]}>
                    {categoria ? `${categoria.icone} ${categoria.singular}` : registro.categoria}
                  </Text>
                </View>
              );
            }) : (
              <View style={styles.semHistorico}>
                <Ionicons name="time-outline" size={24} color="#9b9bb4" />
                <Text style={styles.semHistoricoTitulo}>Histórico mensal ainda indisponível</Text>
                <Text style={styles.semHistoricoTexto}>Ainda não há classificação mensal fechada para este prato.</Text>
              </View>
            )}
          </ScrollView>
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  fundo: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.62)' },
  areaFechar: { flex: 1 },
  folha: { maxHeight: '86%', backgroundColor: '#17172f', borderTopLeftRadius: 22, borderTopRightRadius: 22, borderWidth: 1, borderColor: '#35355c' },
  puxador: { width: 42, height: 4, borderRadius: 2, backgroundColor: '#696985', alignSelf: 'center', marginTop: 9 },
  cabecalho: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: '#303055' },
  cabecalhoTexto: { flex: 1, paddingRight: 12 },
  sobretitulo: { color: '#9e98ff', fontSize: 12, fontWeight: 'bold', textTransform: 'uppercase' },
  titulo: { color: '#fff', fontSize: 21, fontWeight: 'bold', marginTop: 3 },
  conteudo: { padding: 18, paddingBottom: 34 },
  atual: { backgroundColor: '#20203b', borderWidth: 1, borderRadius: 14, padding: 15 },
  categoriaAtual: { color: '#fff', fontSize: 20, fontWeight: 'bold', marginTop: 8, textAlign: 'center' },
  explicacao: { color: '#c8c8db', fontSize: 13, marginTop: 4, textAlign: 'center' },
  resumo: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginTop: 12 },
  metrica: { width: '47%', backgroundColor: '#20203b', borderRadius: 11, padding: 12 },
  rotulo: { color: '#9292ad', fontSize: 11 },
  valor: { color: '#fff', fontSize: 14, fontWeight: 'bold', marginTop: 4 },
  historicoTitulo: { color: '#fff', fontSize: 17, fontWeight: 'bold', marginTop: 22, marginBottom: 9 },
  grafico: { flexDirection: 'row', height: 130, alignItems: 'flex-end', gap: 5, paddingVertical: 8 },
  coluna: { flex: 1, alignItems: 'center', justifyContent: 'flex-end' },
  barra: { width: '75%', backgroundColor: '#8f89ff', borderTopLeftRadius: 4, borderTopRightRadius: 4 },
  graficoValor: { color: '#d7d7e8', fontSize: 8, marginBottom: 4 },
  graficoMes: { color: '#b8b8cc', fontSize: 10, marginTop: 5 },
  registro: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#30304c', paddingVertical: 12 },
  mes: { color: '#d7d7e8', fontSize: 13 },
  categoriaHistorica: { fontSize: 13, fontWeight: 'bold' },
  semHistorico: { alignItems: 'center', backgroundColor: '#20203b', borderRadius: 12, padding: 18 },
  semHistoricoTitulo: { color: '#d9d9e8', fontSize: 14, fontWeight: 'bold', marginTop: 8 },
  semHistoricoTexto: { color: '#9b9bb4', fontSize: 12, lineHeight: 18, textAlign: 'center', marginTop: 5 },
});
