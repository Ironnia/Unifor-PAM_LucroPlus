import React, { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Modal, Platform, RefreshControl, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { promocoesApi } from '../services/api';
import { formatarData, formatarQuantidade } from './alertasViewModel';
import { avisosSugestao, carregarPromocoes, erroPromocao, executarPromocao, hojeLocal, moeda, validarAtivacao } from './promocoesViewModel';

function Botao({ children, onPress, disabled, secundario = false }) {
  return <TouchableOpacity accessibilityRole="button" accessibilityState={{ disabled: Boolean(disabled) }} disabled={disabled} onPress={onPress}
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

export default function PromocoesScreen() {
  const [dados, setDados] = useState(null);
  const [aba, setAba] = useState('PENDENTES');
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState('');
  const [feedback, setFeedback] = useState('');
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ descontoPct: '', dataInicio: '', dataFim: '' });
  const [erroForm, setErroForm] = useState('');
  const [executando, setExecutando] = useState(false);
  const bloqueio = useRef(false);
  const versao = useRef(0);
  const ativo = useRef(false);

  const carregar = useCallback(async () => {
    const pedido = ++versao.current;
    setCarregando(true);
    setErro('');
    try {
      const novos = await carregarPromocoes(promocoesApi);
      if (ativo.current && pedido === versao.current) setDados(novos);
    } catch (error) {
      if (ativo.current && pedido === versao.current) setErro(erroPromocao(error));
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
    setForm({ descontoPct: '', dataInicio: hojeLocal(), dataFim: '' });
    setModal({ item, acao });
  };

  const executar = async () => {
    if (!modal || bloqueio.current) return;
    let corpo;
    try {
      if (modal.acao === 'ATIVAR') corpo = validarAtivacao(form, modal.item.prazoLimiteVenda);
    } catch (error) { setErroForm(error.message); return; }
    bloqueio.current = true;
    setExecutando(true);
    setErroForm('');
    setFeedback('');
    versao.current += 1;
    try {
      const resultado = await executarPromocao(promocoesApi, modal.item, modal.acao, corpo);
      if (!ativo.current) return;
      setModal(null);
      setFeedback(modal.acao === 'ATIVAR' ? 'Campanha lançada.' : modal.acao === 'RECUSAR' ? 'Sugestão encerrada.' : modal.acao === 'DESCARTAR' ? 'Pendência descartada.'
        : modal.acao === 'SUCESSO' ? `Sucesso registrado. Valor salvo: ${moeda(resultado.valorSalvo)}.` : 'Resultado com perda registrado. Nenhum valor salvo foi acrescentado.');
      setDados((anterior) => anterior && ({ ...anterior, resumo: null,
        pendentes: anterior.pendentes.filter((item) => item.loteId !== modal.item.loteId),
        andamento: anterior.andamento.filter((item) => item.id !== modal.item.id),
      }));
      if (modal.acao === 'ATIVAR') setAba('ANDAMENTO');
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

  const renderItem = ({ item }) => <View style={styles.card}>
    <Text style={styles.titulo}>{item.pratoNome || item.ingredienteNome || 'Lote salvo'}</Text>
    <Text style={styles.texto}>{item.pratoNome && item.ingredienteNome ? `${item.ingredienteNome} · ` : ''}Lote {item.numeroLote || `#${item.loteId}`}</Text>
    {item.prazoLimiteVenda && <Text style={styles.texto}>Prazo de venda: {formatarData(item.prazoLimiteVenda)}</Text>}
    {item.semCandidato ? <>
      <Text style={styles.aviso}>Não há recomendação com vendas válidas para este lote. Você pode descartar a pendência sem lançar campanha.</Text>
      <Botao secundario disabled={executando || carregando} onPress={() => abrir(item, 'DESCARTAR')}>Descartar pendência</Botao>
    </> : item.status === 'EM_ANDAMENTO' ? <>
      <Text style={styles.destaque}>{item.descontoPct}% de desconto</Text>
      <Text style={styles.texto}>{formatarData(item.dataInicio)} até {formatarData(item.dataFim)}</Text>
      <Text style={styles.texto}>Estoque de referência: {formatarQuantidade(item.quantidadeReferenciaG)}</Text>
      <Text style={styles.texto}>Custo em risco: {moeda(item.valorEmRisco)}</Text>
      <Text style={styles.aviso}>{item.aguardaConfirmacao ? 'O período terminou. O lote foi totalmente escoado?' : 'A confirmação estará disponível após o último dia da campanha.'}</Text>
      <Botao disabled={executando || carregando || !item.aguardaConfirmacao} onPress={() => abrir(item, 'SUCESSO')}>Sim, 100% vendido</Botao>
      <Botao secundario disabled={executando || carregando || !item.aguardaConfirmacao} onPress={() => abrir(item, 'PERDA')}>Não, houve perda</Botao>
    </> : <>
      {!item.id && <InformacoesSugestao item={item} />}
      {item.id && <Text style={styles.texto}>Sugestão registrada, aguardando desconto e período.</Text>}
      <Botao disabled={executando || carregando} onPress={() => abrir(item, 'ATIVAR')}>Definir desconto e lançar</Botao>
      <Botao secundario disabled={executando || carregando} onPress={() => abrir(item, 'RECUSAR')}>Descartar sugestão</Botao>
    </>}
  </View>;

  const lista = dados ? (aba === 'PENDENTES' ? dados.pendentes : dados.andamento) : [];
  return <View style={styles.container}>
    <View style={styles.resumo}><Text style={styles.texto}>Resumo financeiro: aguardando a apuração vigente da E6.</Text>
      <Text style={styles.texto}>Em andamento: {dados?.resumo?.emAndamento ?? '—'}</Text></View>
    <View style={styles.abas}>{[['PENDENTES', 'Pendentes'], ['ANDAMENTO', 'Em andamento']].map(([valor, titulo]) =>
      <TouchableOpacity key={valor} accessibilityRole="tab" accessibilityState={{ selected: aba === valor }} onPress={() => setAba(valor)} style={[styles.aba, aba === valor && styles.abaAtiva]}><Text style={styles.botaoTexto}>{titulo}</Text></TouchableOpacity>)}</View>
    {feedback ? <Text accessibilityRole="alert" style={styles.feedback}>{feedback}</Text> : null}
    {erro ? <View style={styles.resumo}><Text accessibilityRole="alert" style={styles.aviso}>{erro}{dados ? ' Os dados exibidos podem estar desatualizados.' : ''}</Text><Botao disabled={carregando || executando} onPress={carregar}>Tentar novamente</Botao></View> : null}
    {carregando && !dados ? <ActivityIndicator style={styles.resumo} color="#8f89ff" accessibilityLabel="Carregando promoções" /> :
      <FlatList data={lista} keyExtractor={(item) => item.chave || `p-${item.id}`} renderItem={renderItem} contentContainerStyle={styles.lista}
        refreshControl={<RefreshControl refreshing={carregando} onRefresh={() => !bloqueio.current && carregar()} tintColor="#8f89ff" />}
        ListEmptyComponent={!erro && <Text style={styles.texto}>{aba === 'PENDENTES' ? 'Nenhuma promoção pendente. Use Salvar Lote na aba Alertas.' : 'Nenhuma campanha em andamento.'}</Text>} />}
    <Modal visible={Boolean(modal)} transparent animationType="fade" onRequestClose={() => !bloqueio.current && setModal(null)}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.sombra}>
        <View style={styles.modal}><ScrollView keyboardShouldPersistTaps="handled">
          <Text style={styles.titulo}>{modal?.acao === 'ATIVAR' ? 'Lançar campanha' : ['RECUSAR', 'DESCARTAR'].includes(modal?.acao) ? 'Descartar esta pendência?' : 'Confirmar resultado?'}</Text>
          <Text style={styles.texto}>{modal?.item.pratoNome || modal?.item.ingredienteNome || 'Lote'} · Lote #{modal?.item.loteId}</Text>
          {modal?.acao === 'ATIVAR' ? <>
            <Text style={styles.texto}>Preencha o desconto e o período. O fim inclui todo o dia informado.{modal.item.prazoLimiteVenda ? ` Prazo do lote: ${formatarData(modal.item.prazoLimiteVenda)}.` : ' O servidor conferirá o prazo do lote.'}</Text>
            {[['descontoPct', 'Desconto (%)', 'Ex.: 20'], ['dataInicio', 'Início (AAAA-MM-DD)', 'AAAA-MM-DD'], ['dataFim', 'Fim (AAAA-MM-DD)', 'AAAA-MM-DD']].map(([campo, label, placeholder]) => <View key={campo}>
              <Text style={styles.label}>{label}</Text><TextInput accessibilityLabel={label} value={form[campo]} onChangeText={(texto) => setForm((atual) => ({ ...atual, [campo]: texto }))}
                editable={!executando} keyboardType={campo === 'descontoPct' ? 'number-pad' : 'default'} autoCapitalize="none" maxLength={campo === 'descontoPct' ? 2 : 10} placeholder={placeholder} placeholderTextColor="#9696ac" style={styles.input} />
            </View>)}
          </> : <Text style={styles.texto}>{['RECUSAR', 'DESCARTAR'].includes(modal?.acao) ? 'A pendência será encerrada. Nenhum prato alternativo será oferecido e nenhuma campanha será lançada.' : modal?.acao === 'SUCESSO' ? 'Você declara que todo o estoque de referência foi vendido. O custo recuperado será contabilizado uma única vez. O app não altera o estoque nem o PDV. O resultado não poderá ser trocado depois.' : 'A campanha será encerrada com perda, sem acrescentar valor salvo. O resultado não poderá ser trocado depois.'}</Text>}
          {erroForm ? <Text accessibilityRole="alert" style={styles.aviso}>{erroForm}</Text> : null}
          <Botao disabled={executando} onPress={executar}>{executando ? 'Registrando...' : 'Confirmar'}</Botao>
          <Botao secundario disabled={executando} onPress={() => setModal(null)}>Cancelar</Botao>
        </ScrollView></View>
      </KeyboardAvoidingView>
    </Modal>
  </View>;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' },
  resumo: { padding: 12 }, lista: { padding: 14, flexGrow: 1 },
  abas: { flexDirection: 'row' }, aba: { flex: 1, padding: 12, alignItems: 'center', backgroundColor: '#20203c' }, abaAtiva: { borderBottomWidth: 3, borderBottomColor: '#8f89ff' },
  card: { padding: 16, marginBottom: 12, borderRadius: 12, backgroundColor: '#1a1a33', borderWidth: 1, borderColor: '#303055' },
  titulo: { fontSize: 19, fontWeight: 'bold', color: '#fff', marginBottom: 8 }, texto: { fontSize: 14, lineHeight: 21, color: '#c5c5d8', marginBottom: 6 },
  destaque: { color: '#aaa5ff', fontSize: 18, marginVertical: 8 }, aviso: { color: '#ffca80', lineHeight: 21, marginVertical: 8 }, feedback: { color: '#8de2b3', padding: 12 },
  botao: { padding: 13, borderRadius: 8, backgroundColor: '#6258df', alignItems: 'center', marginTop: 9 }, secundario: { backgroundColor: '#34344e' }, desabilitado: { opacity: 0.45 }, botaoTexto: { color: '#fff', fontWeight: '600', textAlign: 'center' },
  sombra: { flex: 1, backgroundColor: '#000a', justifyContent: 'center', padding: 20 }, modal: { backgroundColor: '#1a1a33', borderRadius: 14, padding: 20, maxHeight: '90%', width: '100%', maxWidth: 520, alignSelf: 'center' },
  label: { color: '#fff', marginTop: 12, marginBottom: 5 }, input: { backgroundColor: '#0f0f23', color: '#fff', borderWidth: 1, borderColor: '#626283', borderRadius: 7, padding: 12 },
});
