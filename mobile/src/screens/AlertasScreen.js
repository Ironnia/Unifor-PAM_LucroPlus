import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator, FlatList, Modal, RefreshControl, StyleSheet, Text,
  TouchableOpacity, View,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { alertasApi, promocoesApi } from '../services/api';
import {
  FILTROS_ALERTA, contarAlertas, filtrarAlertas, formatarData,
  formatarQuantidade, textoPrazo, textoPrevia, validarListaAlertas,
} from './alertasViewModel';

const mensagemErro = (error, assunto) => {
  if (error.code === 'ECONNABORTED') return 'A API demorou para responder. Verifique a rede e tente novamente.';
  if (error.message?.includes('formato inesperado')) return error.message;
  if (error.response?.status === 409 || error.response?.status === 404) {
    return 'Este alerta já mudou no servidor. Atualize a lista e tente novamente.';
  }
  return error.response?.data?.erro || `Não foi possível ${assunto}. Verifique a conexão e tente novamente.`;
};

const rotuloFiltro = { TODOS: 'Todos', CRITICO: 'Críticos', ATENCAO: 'Atenção' };
export default function AlertasScreen({ onCountChange }) {
  const jaCarregou = useRef(false);
  const [aba, setAba] = useState('ALERTAS');
  const [filtro, setFiltro] = useState('TODOS');
  const [alertas, setAlertas] = useState([]);
  const [pendentes, setPendentes] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState('');
  const [aviso, setAviso] = useState('');
  const [carregandoPendentes, setCarregandoPendentes] = useState(false);
  const [erroPendentes, setErroPendentes] = useState('');
  const [confirmacao, setConfirmacao] = useState(null);
  const [executando, setExecutando] = useState(false);
  const [feedback, setFeedback] = useState('');

  const carregarAlertas = useCallback(async ({ preservar = false } = {}) => {
    setErro('');
    setAviso('');
    try {
      const novos = validarListaAlertas(await alertasApi.getAtivos());
      setAlertas(novos);
      onCountChange?.(novos.length);
    } catch (error) {
      const texto = mensagemErro(error, 'carregar os alertas');
      if (preservar) setAviso(`${texto} Os dados anteriores foram mantidos.`);
      else {
        setAlertas([]);
        onCountChange?.(0);
        setErro(texto);
      }
    } finally {
      jaCarregou.current = true;
      setCarregando(false);
      setAtualizando(false);
    }
  }, [onCountChange]);

  const carregarPendentes = useCallback(async () => {
    setCarregandoPendentes(true);
    setErroPendentes('');
    try {
      const dados = await promocoesApi.getPendentes();
      if (!Array.isArray(dados)) throw new Error('A API retornou um formato inesperado para as promoções pendentes.');
      setPendentes(dados);
    } catch (error) {
      setErroPendentes(mensagemErro(error, 'carregar as promoções pendentes'));
    } finally {
      setCarregandoPendentes(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    carregarAlertas({ preservar: jaCarregou.current });
  }, [carregarAlertas]));

  useEffect(() => {
    if (aba === 'PROMOCOES') carregarPendentes();
  }, [aba, carregarPendentes]);

  const atualizar = () => {
    setAtualizando(true);
    if (aba === 'PROMOCOES') {
      carregarPendentes().finally(() => setAtualizando(false));
    } else {
      carregarAlertas({ preservar: true });
    }
  };

  const executarAcao = async () => {
    if (!confirmacao || executando) return;
    const { alerta, acao } = confirmacao;
    setExecutando(true);
    setFeedback('');
    try {
      if (acao === 'SALVAR') await alertasApi.salvarLote(alerta.id);
      else await alertasApi.marcarCiente(alerta.id);

      const restantes = alertas.filter((item) => item.id !== alerta.id);
      setAlertas(restantes);
      onCountChange?.(restantes.length);
      setConfirmacao(null);
      setFeedback(acao === 'SALVAR'
        ? 'Lote salvo. Consulte a aba Promoções pendentes.'
        : 'Alerta marcado como ciente.');
      await carregarAlertas({ preservar: true });
      if (acao === 'SALVAR' && aba === 'PROMOCOES') await carregarPendentes();
    } catch (error) {
      setConfirmacao(null);
      setAviso(mensagemErro(error, 'concluir a ação'));
    } finally {
      setExecutando(false);
    }
  };

  const contagens = contarAlertas(alertas);
  const visiveis = filtrarAlertas(alertas, filtro);

  const renderAlerta = ({ item }) => {
    const critico = item.criticidade === 'CRITICO';
    const cor = critico ? '#ff6868' : '#ffbf69';
    return (
      <View style={styles.card}>
        <View style={styles.cardHeading}>
          <Text style={styles.ingrediente}>{item.lote?.ingrediente?.nome || 'Ingrediente não informado'}</Text>
          <Text style={[styles.status, { color: cor, borderColor: cor }]}>{critico ? 'CRÍTICO' : 'ATENÇÃO'}</Text>
        </View>
        <Text style={styles.detalhe}>Lote #{item.loteId ?? item.lote?.id ?? '—'} · {formatarQuantidade(item.lote?.quantidade)}</Text>
        <Text style={styles.prazo}>Validade: {formatarData(item.dataValidade)}</Text>
        <Text style={styles.prazo}>Aja até: {formatarData(item.prazoLimiteVenda)}</Text>
        <Text style={[styles.dias, { color: cor }]}>{textoPrazo(item.diasParaPrazoLimite)}</Text>
        <Text style={styles.detalhe}>{textoPrevia(item.previaConsultiva)}</Text>
        <View style={styles.acoes}>
          <TouchableOpacity
            style={[styles.botao, styles.salvar]}
            onPress={() => setConfirmacao({ alerta: item, acao: 'SALVAR' })}
            disabled={executando}
            accessibilityRole="button"
            accessibilityLabel={`Salvar lote do alerta ${item.id}`}
          ><Text style={styles.botaoTexto}>Salvar Lote</Text></TouchableOpacity>
          <TouchableOpacity
            style={[styles.botao, styles.ciente]}
            onPress={() => setConfirmacao({ alerta: item, acao: 'CIENTE' })}
            disabled={executando}
            accessibilityRole="button"
            accessibilityLabel={`Marcar alerta ${item.id} como ciente`}
          ><Text style={styles.botaoTexto}>Marcar Ciente</Text></TouchableOpacity>
        </View>
      </View>
    );
  };

  const renderPendente = ({ item }) => (
    <View style={styles.card}>
      <Text style={styles.ingrediente}>{item.ingredienteNome || 'Ingrediente não informado'}</Text>
      <Text style={styles.detalhe}>Lote {item.numeroLote || `#${item.loteId}`}</Text>
      <Text style={styles.prazo}>Prazo limite de venda: {formatarData(item.prazoLimiteVenda)}</Text>
      <Text style={styles.pendente}>Salvo para a etapa de promoções</Text>
    </View>
  );

  return (
    <View style={styles.container}>
      <View style={styles.abas}>
        <TouchableOpacity style={[styles.aba, aba === 'ALERTAS' && styles.abaAtiva]} onPress={() => setAba('ALERTAS')} accessibilityRole="tab" accessibilityState={{ selected: aba === 'ALERTAS' }}>
          <Text style={styles.abaTexto}>Alertas {contagens.TODOS > 0 ? `(${contagens.TODOS})` : ''}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.aba, aba === 'PROMOCOES' && styles.abaAtiva]} onPress={() => setAba('PROMOCOES')} accessibilityRole="tab" accessibilityState={{ selected: aba === 'PROMOCOES' }}>
          <Text style={styles.abaTexto}>Promoções pendentes</Text>
        </TouchableOpacity>
      </View>

      {aba === 'ALERTAS' ? (
        <>
          <View style={styles.filtros}>
            {FILTROS_ALERTA.map((valor) => (
              <TouchableOpacity key={valor} style={[styles.filtro, filtro === valor && styles.filtroAtivo]} onPress={() => setFiltro(valor)} accessibilityRole="button" accessibilityState={{ selected: filtro === valor }}>
                <Text style={[styles.filtroTexto, filtro === valor && styles.filtroTextoAtivo]}>{rotuloFiltro[valor]} {contagens[valor]}</Text>
              </TouchableOpacity>
            ))}
          </View>
          {feedback ? <Text accessibilityRole="alert" style={styles.sucesso}>{feedback}</Text> : null}
          {aviso ? <Text accessibilityRole="alert" style={styles.aviso}>{aviso}</Text> : null}
          {carregando ? <View style={styles.centro}><ActivityIndicator color="#6c63ff" /><Text style={styles.detalhe}>Carregando alertas...</Text></View> : erro ? (
            <View style={styles.centro}><Text style={styles.tituloVazio}>Falha ao carregar alertas</Text><Text style={styles.detalhe}>{erro}</Text><TouchableOpacity style={styles.tentar} onPress={() => { setCarregando(true); carregarAlertas(); }}><Text style={styles.botaoTexto}>Tentar novamente</Text></TouchableOpacity></View>
          ) : (
            <FlatList data={visiveis} keyExtractor={(item) => String(item.id)} renderItem={renderAlerta} contentContainerStyle={styles.lista}
              refreshControl={<RefreshControl refreshing={atualizando} onRefresh={atualizar} tintColor="#6c63ff" />}
              ListEmptyComponent={<View style={styles.centro}><Ionicons name="checkmark-circle-outline" size={42} color="#8b8ba7" /><Text style={styles.tituloVazio}>{alertas.length ? 'Nenhum alerta neste filtro' : 'Nenhum alerta ativo'}</Text><Text style={styles.detalhe}>{alertas.length ? 'Selecione outro filtro para ver os demais alertas.' : 'Os lotes dentro do prazo de risco aparecerão aqui.'}</Text></View>}
            />
          )}
        </>
      ) : carregandoPendentes && pendentes.length === 0 ? <View style={styles.centro}><ActivityIndicator color="#6c63ff" /><Text style={styles.detalhe}>Carregando lotes salvos...</Text></View> : (
        <>
          {erroPendentes ? <Text accessibilityRole="alert" style={styles.aviso}>{erroPendentes} <Text onPress={carregarPendentes} style={styles.link}>Tentar novamente</Text></Text> : null}
          <FlatList data={pendentes} keyExtractor={(item) => String(item.alertaId)} renderItem={renderPendente} contentContainerStyle={styles.lista}
            refreshControl={<RefreshControl refreshing={atualizando} onRefresh={atualizar} tintColor="#6c63ff" />}
            ListEmptyComponent={!erroPendentes && !carregandoPendentes ? <View style={styles.centro}><Text style={styles.tituloVazio}>Nenhum lote salvo</Text><Text style={styles.detalhe}>Use Salvar Lote em um alerta para incluí-lo aqui.</Text></View> : null}
          />
        </>
      )}

      <Modal visible={Boolean(confirmacao)} transparent animationType="fade" onRequestClose={() => !executando && setConfirmacao(null)}>
        <View style={styles.sombra}><View style={styles.modal}>
          <Text style={styles.modalTitulo}>{confirmacao?.acao === 'SALVAR' ? 'Salvar este lote?' : 'Marcar como ciente?'}</Text>
          <Text style={styles.modalTexto}>{confirmacao?.acao === 'SALVAR'
            ? 'O alerta sairá da lista e o lote entrará em Promoções pendentes.'
            : 'O alerta sairá da lista sem entrar na fila de promoções.'}</Text>
          <View style={styles.acoes}>
            <TouchableOpacity style={[styles.botao, styles.ciente]} onPress={() => setConfirmacao(null)} disabled={executando}><Text style={styles.botaoTexto}>Cancelar</Text></TouchableOpacity>
            <TouchableOpacity style={[styles.botao, styles.salvar]} onPress={executarAcao} disabled={executando}>{executando ? <ActivityIndicator color="#fff" /> : <Text style={styles.botaoTexto}>Confirmar</Text>}</TouchableOpacity>
          </View>
        </View></View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  abas: { flexDirection: 'row', backgroundColor: '#17172f', borderBottomWidth: 1, borderBottomColor: '#303055' },
  aba: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  abaAtiva: { borderBottomWidth: 2, borderBottomColor: '#8f89ff' },
  abaTexto: { color: '#fff', fontWeight: '600', fontSize: 13 },
  filtros: { flexDirection: 'row', padding: 12, gap: 8 },
  filtro: { backgroundColor: '#20203c', borderRadius: 20, paddingHorizontal: 11, paddingVertical: 8 },
  filtroAtivo: { backgroundColor: '#6c63ff' },
  filtroTexto: { color: '#b8b8cc', fontSize: 12 },
  filtroTextoAtivo: { color: '#fff', fontWeight: 'bold' },
  lista: { padding: 14, flexGrow: 1 },
  card: { backgroundColor: '#1a1a33', borderColor: '#303055', borderWidth: 1, borderRadius: 12, padding: 15, marginBottom: 12 },
  cardHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  ingrediente: { color: '#fff', fontSize: 17, fontWeight: 'bold', flexShrink: 1 },
  status: { fontSize: 10, fontWeight: 'bold', borderWidth: 1, borderRadius: 12, paddingHorizontal: 8, paddingVertical: 3 },
  detalhe: { color: '#b8b8cc', marginTop: 7, fontSize: 13, lineHeight: 19, textAlign: 'center' },
  prazo: { color: '#e0e0ff', marginTop: 12, fontSize: 13 },
  dias: { marginTop: 4, fontWeight: 'bold', fontSize: 12 },
  acoes: { flexDirection: 'row', gap: 9, marginTop: 16 },
  botao: { flex: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 8, paddingVertical: 10, minHeight: 42 },
  salvar: { backgroundColor: '#6c63ff' },
  ciente: { backgroundColor: '#34344f' },
  botaoTexto: { color: '#fff', fontSize: 13, fontWeight: 'bold' },
  centro: { alignItems: 'center', justifyContent: 'center', padding: 28, flex: 1 },
  tituloVazio: { color: '#fff', fontWeight: 'bold', fontSize: 16, marginTop: 10, textAlign: 'center' },
  tentar: { backgroundColor: '#6c63ff', padding: 11, borderRadius: 8, marginTop: 18 },
  aviso: { color: '#ffcf82', backgroundColor: '#493822', padding: 11, fontSize: 13 },
  sucesso: { color: '#c4f5d3', backgroundColor: '#234835', padding: 11, fontSize: 13 },
  link: { textDecorationLine: 'underline', fontWeight: 'bold' },
  pendente: { color: '#bcb7ff', marginTop: 8, fontSize: 12 },
  sombra: { flex: 1, backgroundColor: 'rgba(0,0,0,0.75)', justifyContent: 'center', padding: 24 },
  modal: { backgroundColor: '#20203c', borderRadius: 14, padding: 20 },
  modalTitulo: { color: '#fff', fontSize: 19, fontWeight: 'bold' },
  modalTexto: { color: '#cccce0', marginTop: 10, lineHeight: 21 },
});
