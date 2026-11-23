import React, { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, KeyboardAvoidingView, Modal, Platform, RefreshControl, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { cardapioApi, promocoesApi } from '../services/api';
import { impactoLeve, impactoSucesso } from '../services/nativeService';
import { formatarData, formatarQuantidade } from './alertasViewModel';
import { avisosSugestao, carregarPromocoes, descontoInicial, erroPromocao, executarPromocao, hojeLocal, moeda, validarAtivacao } from './promocoesViewModel';
import CalendarioData from '../components/CalendarioData';
import { Ionicons } from '@expo/vector-icons';

function Botao({ children, onPress, disabled, secundario = false }) {
  return <TouchableOpacity accessibilityRole="button" accessibilityLabel={children} accessibilityState={{ disabled: Boolean(disabled) }} disabled={disabled} onPress={onPress}
    style={[styles.botao, secundario && styles.secundario, disabled && styles.desabilitado]}><Text style={styles.botaoTexto}>{children}</Text></TouchableOpacity>;
}

 
export function InformacoesSugestao({ item }) {
  const avisos = avisosSugestao(item);
  const renderAvisos = (campo) => avisos.filter((aviso) => aviso.campo === campo).map((aviso, indice) =>
    <Text key={`${aviso.codigo}-${aviso.ingredienteId ?? indice}`} style={styles.aviso}>{aviso.mensagem}</Text>);
  return <>
    <Text style={styles.texto}>Melhor dia: {item.melhorDiaSemana || 'Indisponível'}{item.dataSugerida ? ` (${formatarData(item.dataSugerida)})` : ''}</Text>
    {renderAvisos('MELHOR_DIA')}
    <Text style={styles.texto}>Preço de venda: {moeda(item.precoVenda)}</Text>
    <Text style={styles.texto}>Lucro estimado: {item.lucroBrutoEstimado == null ? 'Indisponível' : `${moeda(item.lucroBrutoEstimado)}${item.margemPct == null ? '' : ` (${item.margemPct.toFixed(1)}%)`}`}</Text>
    {renderAvisos('MARGEM')}
    <Text style={styles.texto}>Desconto recomendado: {item.limiteDescontoMargemBrutaPct == null ? 'Indisponível' : `até ${item.limiteDescontoMargemBrutaPct}%`}. A decisão é do gerente.</Text>
    <Text style={styles.texto}>A estimativa considera os ingredientes registrados, não todos os custos do restaurante.</Text>
    {avisos.filter((aviso) => !['MELHOR_DIA', 'MARGEM'].includes(aviso.campo)).map((aviso, indice) =>
      <Text key={`geral-${indice}`} style={styles.aviso}>{aviso.mensagem}</Text>)}
  </>;
}

export function ResumoPratoPendente({ item }) {
  return <View style={styles.resumoPrato}>
    <Text style={styles.texto}>Preço atual: {moeda(item.precoVenda)}</Text>
    <Text style={styles.texto}>Lucro atual: {moeda(item.lucroBrutoEstimado)}</Text>
    <Text style={styles.recomendado}><Ionicons name="pricetag-outline" size={15} color="#c7f9cc" /> Desconto recomendado: {item.limiteDescontoMargemBrutaPct == null ? 'Indisponível' : `até ${item.limiteDescontoMargemBrutaPct}%`}</Text>
  </View>;
}




export default function PromocoesScreen() {
  const [dados, setDados] = useState(null);
  const [aba, setAba] = useState('PENDENTES');
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState('');
  const [feedback, setFeedback] = useState('');
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ descontoPct: '', dataInicio: '', dataFim: '' });
  const [erroForm, setErroForm] = useState('');
  const [campoCalendario, setCampoCalendario] = useState(null);
  const [executando, setExecutando] = useState(false);
  const bloqueio = useRef(false);
  const versao = useRef(0);
  const ativo = useRef(false);

  const carregar = useCallback(async (avisar = false) => {
    const pedido = ++versao.current;
    setCarregando(true);
    setErro('');
    try {
      const novos = await carregarPromocoes(promocoesApi, cardapioApi);
      if (ativo.current && pedido === versao.current) setDados(novos);
    } catch (error) {
      if (ativo.current && pedido === versao.current) {
        const texto = erroPromocao(error);
        setErro(texto);
        if (avisar) Alert.alert('Não foi possível atualizar', texto);
      }
    } finally {
      if (ativo.current && pedido === versao.current) setCarregando(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    ativo.current = true;
    carregar();
    return () => { ativo.current = false; versao.current += 1; };
  }, [carregar]));

  const abrir = (item, acao) => {
    setErroForm('');
    setForm({ descontoPct: descontoInicial(item.limiteDescontoMargemBrutaPct), dataInicio: hojeLocal(), dataFim: '' });
    setModal({ item, acao });
  };

  const executar = async () => {
    if (!modal || bloqueio.current) return;
    let corpo;
    try {
      if (modal.acao === 'ATIVAR') corpo = validarAtivacao(form, modal.item.prazoLimiteVenda);
    } catch (error) { setErroForm(error.message); return; }
    if (modal.acao === 'ATIVAR') void impactoLeve();
     
    bloqueio.current = true;
    setExecutando(true);
    setErroForm('');
    setFeedback('');
    versao.current += 1;
    try {
      const resultado = await executarPromocao(promocoesApi, modal.item, modal.acao, corpo);
      if (!ativo.current) return;
      setModal(null);
      setFeedback(modal.acao === 'ATIVAR' ? 'Campanha lançada.' : modal.acao === 'CANCELAR' ? 'Promoção cancelada. O lote voltou para Pendentes.' : modal.acao === 'RECUSAR' ? 'Sugestão encerrada.' : modal.acao === 'DESCARTAR' ? 'Pendência descartada.'
        : modal.acao === 'SUCESSO' ? `Sucesso registrado. Valor salvo: ${moeda(resultado.valorSalvo)}.` : 'Resultado com perda registrado. Nenhum valor salvo foi acrescentado.');
       
       
      setDados((anterior) => anterior && ({ ...anterior, resumo: null,
        pendentes: anterior.pendentes.filter((item) => item.loteId !== modal.item.loteId),
        andamento: anterior.andamento.filter((item) => item.id !== modal.item.id),
      }));
      if (modal.acao === 'ATIVAR') setAba('ANDAMENTO');
      if (modal.acao === 'CANCELAR') setAba('PENDENTES');
      if (modal.acao === 'ATIVAR') void impactoSucesso();
      await carregar();
    } catch (error) {
      if (ativo.current) {
        if ([404, 409].includes(error.response?.status)) {
          setModal(null);
          setFeedback(erroPromocao(error));
        } else setErroForm(erroPromocao(error));
        await carregar();
      }
    } finally {
      bloqueio.current = false;
      setExecutando(false);
    }
  };

  const renderItem = ({ item }) => <View style={[styles.card, { borderColor: item.criticidade === 'CRITICO' ? '#ff6868' : item.criticidade === 'ATENCAO' ? '#ffbf69' : '#303055' }]}>
    <Text style={[styles.titulo, { textAlign: 'center' }]}>{item.pratoNome || item.ingredienteNome || 'Lote salvo'}</Text>
    <Text style={[styles.texto, { textAlign: 'center' }]}>Lote: {item.ingredienteNome || 'Ingrediente'} #{String(item.numeroLote || item.loteId).replace(/^#/, '')}</Text>
    {item.prazoLimiteVenda && <Text style={styles.prazoTag}>Prazo: {formatarData(item.prazoLimiteVenda)}</Text>}
    {item.semCandidato ? <>
      <Text style={styles.aviso}>Não há recomendação com vendas válidas para este lote. Você pode descartar a pendência sem lançar campanha.</Text>
      <Botao secundario disabled={executando || carregando} onPress={() => abrir(item, 'DESCARTAR')}>Descartar pendência</Botao>
    </> : item.status === 'EM_ANDAMENTO' ? <>
      <Text style={[styles.destaque, styles.centralizado]}>{item.descontoPct}% de desconto</Text>
      <Text style={[styles.texto, styles.centralizado]}>{formatarData(item.dataInicio)} até {formatarData(item.dataFim)}</Text>
      <Text style={styles.texto}>Estoque de referência: {formatarQuantidade(item.quantidadeReferenciaG)}</Text>
      <Text style={styles.texto}>Custo em risco: {moeda(item.valorEmRisco)}</Text>
      <Text style={styles.aviso}>{item.aguardaConfirmacao ? 'O período terminou. O lote foi totalmente escoado?' : 'A confirmação estará disponível após o último dia da campanha.'}</Text>
      <Botao disabled={executando || carregando || !item.aguardaConfirmacao} onPress={() => abrir(item, 'SUCESSO')}>Sim, 100% vendido</Botao>
      <Botao secundario disabled={executando || carregando || !item.aguardaConfirmacao} onPress={() => abrir(item, 'PERDA')}>Não, houve perda</Botao>
      <Botao secundario disabled={executando || carregando} onPress={() => abrir(item, 'CANCELAR')}>Cancelar promoção</Botao>
    </> : <>
      <ResumoPratoPendente item={item} />
      <Botao disabled={executando || carregando} onPress={() => abrir(item, 'ATIVAR')}>Definir desconto e lançar</Botao>
      <Botao secundario disabled={executando || carregando} onPress={() => abrir(item, 'RECUSAR')}>Descartar sugestão</Botao>
    </>}
  </View>;

  const lista = dados ? (aba === 'PENDENTES' ? dados.pendentes : dados.andamento) : [];
  return <View style={styles.container}>
    <View style={styles.resumo}><View style={styles.resumoBlocos}><View style={styles.resumoBloco}><Text style={styles.resumoRotulo}><Ionicons name="cash-outline" size={13} color="#b8b8cc" /> Valor salvo no mês</Text><Text style={styles.resumoValor}>{dados?.resumo ? moeda(dados.resumo.valorSalvoMes) : '—'}</Text></View><View style={styles.resumoBloco}><Text style={styles.resumoRotulo}><Ionicons name="warning-outline" size={13} color="#b8b8cc" /> Valor em risco (ativas)</Text><Text style={styles.resumoValor}>{dados?.resumo ? moeda(dados.resumo.valorEmRiscoAtivo) : '—'}</Text></View></View></View>
    <View style={styles.abas}>{[['PENDENTES', 'time-outline', 'Pendentes', dados?.pendentes.length || 0], ['ANDAMENTO', 'rocket-outline', 'Em andamento', dados?.andamento.length || 0]].map(([valor, icone, titulo, quantidade]) =>
      <TouchableOpacity key={valor} accessibilityRole="button" accessibilityLabel={titulo} accessibilityState={{ selected: aba === valor }} onPress={() => setAba(valor)} style={[styles.aba, aba === valor && styles.abaAtiva]}><Text style={styles.botaoTexto}><Ionicons name={icone} size={15} color="#fff" /> {titulo} ({quantidade})</Text></TouchableOpacity>)}</View>
    {feedback ? <Text accessibilityRole="alert" style={styles.feedback}>{feedback}</Text> : null}
    {erro ? <View style={styles.resumo}><Text accessibilityRole="alert" style={styles.aviso}>{erro}{dados ? ' Os dados exibidos podem estar desatualizados.' : ''}</Text><Botao disabled={carregando || executando} onPress={carregar}>Tentar novamente</Botao></View> : null}
    {carregando && !dados ? <ActivityIndicator style={styles.resumo} color="#8f89ff" accessibilityLabel="Carregando promoções" /> :
      <FlatList data={lista} keyExtractor={(item) => item.chave || `p-${item.id}`} renderItem={renderItem} contentContainerStyle={styles.lista}
        refreshControl={<RefreshControl refreshing={carregando} onRefresh={() => !bloqueio.current && carregar(true)} tintColor="#8f89ff" />}
        ListEmptyComponent={!erro && <Text style={styles.texto}>{aba === 'PENDENTES' ? 'Nenhuma promoção pendente. Use Salvar Lote na aba Alertas.' : 'Nenhuma campanha em andamento.'}</Text>} />}
    <Modal visible={Boolean(modal)} transparent animationType="fade" onRequestClose={() => !bloqueio.current && setModal(null)}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.sombra}>
        <View style={styles.modal}><ScrollView keyboardShouldPersistTaps="handled">
          <Text style={styles.titulo}>{modal?.acao === 'ATIVAR' ? 'Lançar campanha' : modal?.acao === 'CANCELAR' ? 'Cancelar promoção?' : ['RECUSAR', 'DESCARTAR'].includes(modal?.acao) ? 'Descartar esta pendência?' : 'Confirmar resultado?'}</Text>
          <Text style={styles.texto}>Prato: {modal?.item.pratoNome || 'Sem prato recomendado'}</Text>
          <Text style={styles.texto}>Ingrediente: {modal?.item.ingredienteNome || 'Não informado'}</Text>
          <Text style={styles.texto}>Lote #{String(modal?.item.numeroLote || modal?.item.loteId || '—').replace(/^#/, '')}</Text>
          {modal?.acao === 'ATIVAR' ? <>
            <Text style={styles.texto}>Prazo do lote: {formatarData(modal.item.prazoLimiteVenda)}</Text>
            <Text style={styles.label}>Desconto (%)</Text><TextInput accessibilityLabel="Desconto (%)" value={form.descontoPct} onChangeText={(texto) => setForm((atual) => ({ ...atual, descontoPct: texto }))} editable={!executando} keyboardType="number-pad" maxLength={2} style={styles.input} />
            {[['dataInicio', 'Início'], ['dataFim', 'Fim']].map(([campo, label]) => <View key={campo}><Text style={styles.label}>{label}</Text><TouchableOpacity style={styles.input} onPress={() => setCampoCalendario(campo)} accessibilityRole="button" accessibilityLabel={`Selecionar data de ${label.toLowerCase()}`}><Text style={styles.texto}>{form[campo] ? formatarData(form[campo]) : <>DD/MM/AAAA <Ionicons name="calendar-outline" size={15} color="#c5c5d8" /></>}</Text></TouchableOpacity></View>)}
          </> : ['RECUSAR', 'DESCARTAR'].includes(modal?.acao) ? null : <Text style={styles.texto}>{modal?.acao === 'CANCELAR' ? 'A promoção voltará para Pendentes. Você poderá definir outro desconto e período.' : modal?.acao === 'SUCESSO' ? 'Você declara que todo o estoque de referência foi vendido. O custo recuperado será contabilizado uma única vez. O app não altera o estoque nem o PDV. O resultado não poderá ser trocado depois.' : 'A campanha será encerrada com perda, sem acrescentar valor salvo. O resultado não poderá ser trocado depois.'}</Text>}
          {erroForm ? <Text accessibilityRole="alert" style={styles.aviso}>{erroForm}</Text> : null}
          <Botao disabled={executando} onPress={executar}>{executando ? 'Registrando...' : 'Confirmar'}</Botao>
          <Botao secundario disabled={executando} onPress={() => setModal(null)}>Cancelar</Botao>
        </ScrollView></View>
      </KeyboardAvoidingView>
    </Modal>
    <CalendarioData visivel={Boolean(campoCalendario)} valor={campoCalendario ? form[campoCalendario] : ''} minimo={campoCalendario === 'dataFim' ? form.dataInicio : hojeLocal()} maximo={modal?.item.prazoLimiteVenda} onSelect={(data) => setForm((atual) => ({ ...atual, [campoCalendario]: data, ...(campoCalendario === 'dataInicio' && atual.dataFim && atual.dataFim < data ? { dataFim: '' } : {}) }))} onClose={() => setCampoCalendario(null)} />
  </View>;
}

const styles = StyleSheet.create({
  resumoBlocos: { flexDirection: 'row', gap: 10 }, resumoBloco: { flex: 1, backgroundColor: '#20203c', borderRadius: 12, padding: 12 }, resumoRotulo: { color: '#b8b8cc', fontSize: 11 }, resumoValor: { color: '#fff', fontWeight: 'bold', fontSize: 16, marginTop: 5 },
  prazoTag: { alignSelf: 'center', color: '#ffcf82', borderColor: '#ffbf69', borderWidth: 1, borderRadius: 12, paddingHorizontal: 10, paddingVertical: 4, marginBottom: 10 },
  resumoPrato: { borderTopWidth: 1, borderTopColor: '#303055', paddingTop: 10 }, recomendado: { color: '#c7f9cc', fontWeight: 'bold', fontSize: 15, marginTop: 5, marginBottom: 10 },
  container: { flex: 1, backgroundColor: '#0f0f23' },
  resumo: { padding: 12 }, lista: { padding: 14, flexGrow: 1 },
  abas: { flexDirection: 'row' }, aba: { flex: 1, padding: 12, minHeight: 44, justifyContent: 'center', alignItems: 'center', backgroundColor: '#20203c' }, abaAtiva: { borderBottomWidth: 3, borderBottomColor: '#8f89ff' },
  card: { padding: 16, marginBottom: 12, borderRadius: 12, backgroundColor: '#1a1a33', borderWidth: 1, borderColor: '#303055' },
  titulo: { fontSize: 19, fontWeight: 'bold', color: '#fff', marginBottom: 8 }, texto: { fontSize: 14, lineHeight: 21, color: '#c5c5d8', marginBottom: 6 },
  destaque: { color: '#aaa5ff', fontSize: 18, marginVertical: 8 }, aviso: { color: '#ffca80', lineHeight: 21, marginVertical: 8 }, feedback: { color: '#8de2b3', padding: 12 },
  centralizado: { textAlign: 'center' },
  botao: { padding: 13, minHeight: 44, justifyContent: 'center', borderRadius: 8, backgroundColor: '#6258df', alignItems: 'center', marginTop: 9 }, secundario: { backgroundColor: '#34344e' }, desabilitado: { opacity: 0.45 }, botaoTexto: { color: '#fff', fontWeight: '600', textAlign: 'center' },
  sombra: { flex: 1, backgroundColor: '#000a', justifyContent: 'center', padding: 20 }, modal: { backgroundColor: '#1a1a33', borderRadius: 14, padding: 20, maxHeight: '90%', width: '100%', maxWidth: 520, alignSelf: 'center' },
  label: { color: '#fff', marginTop: 12, marginBottom: 5 }, input: { backgroundColor: '#0f0f23', color: '#fff', borderWidth: 1, borderColor: '#626283', borderRadius: 7, padding: 12 },
});
