import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { estoqueApi } from '../services/estoqueApi';
import {
  aparenciaFaixa,
  FaixaPrevisao,
  filtrarItens,
  formatarCompra,
  formatarGramas,
  validarPrevisaoCompras,
} from './previsaoComprasViewModel';

const mensagemErro = (error) => error.response?.data?.erro
  || error.message
  || 'Não foi possível carregar a previsão de compras.';
const formatarKg = (gramas) => `${new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(gramas / 1000)} kg`;

export default function PrevisaoComprasSubScreen() {
  const [dados, setDados] = useState(null);
  const [filtro, setFiltro] = useState('TODOS');
  const [carregando, setCarregando] = useState(true);
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState('');

  const carregar = useCallback(async (preservar = false) => {
    setErro('');
    try {
      setDados(validarPrevisaoCompras(await estoqueApi.getPrevisaoCompras()));
    } catch (error) {
      if (!preservar) setDados(null);
      setErro(mensagemErro(error));
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useEffect(() => { carregar(); }, [carregar]);
  const itens = useMemo(() => filtrarItens(dados?.itens || [], filtro), [dados, filtro]);
  const atualizar = () => { setAtualizando(true); carregar(true); };

  if (carregando) {
    return <View style={styles.centro}><ActivityIndicator size="large" color="#6c63ff" /><Text style={styles.textoSecundario}>Calculando a previsão...</Text></View>;
  }
  if (!dados) {
    return (
      <View style={styles.centro}>
        <Ionicons name="cloud-offline-outline" size={48} color="#ff5252" />
        <Text style={styles.tituloVazio}>Falha ao carregar a previsão</Text>
        <Text style={styles.textoErro}>{erro}</Text>
        <TouchableOpacity style={styles.botaoRepetir} onPress={() => { setCarregando(true); carregar(); }}>
          <Text style={styles.botaoRepetirTexto}>Tentar novamente</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const filtros = [
    [FaixaPrevisao.URGENTE, '🔴', dados.resumo.urgentes, 'Urgentes'],
    [FaixaPrevisao.ATENCAO, '🟡', dados.resumo.atencao, 'Atenção'],
    [FaixaPrevisao.SEGURO, '🟢', dados.resumo.seguros, 'Seguros'],
  ];

  const renderItem = ({ item }) => {
    const faixa = aparenciaFaixa(item.faixa);
    if (item.faixa === FaixaPrevisao.DADOS_INSUFICIENTES) {
      return (
        <View style={styles.card}>
          <Text style={styles.nome}>{item.ingredienteNome}</Text>
          <View style={styles.insuficiente}><Ionicons name={faixa.icone} size={18} color={faixa.cor} /><Text style={styles.insuficienteTexto}>Cálculo incompleto: dados insuficientes</Text></View>
          <Text style={styles.motivo}>{item.motivoDadosInsuficientes}</Text>
        </View>
      );
    }
    const largura = `${Math.min(100, item.coberturaPct)}%`;
    return (
      <View style={styles.card}>
        <View style={styles.cardTopo}>
          <Text style={styles.nome}>{item.ingredienteNome}</Text>
          <View style={[styles.badge, { borderColor: faixa.cor }]}><Ionicons name={faixa.icone} size={14} color={faixa.cor} /><Text style={[styles.badgeTexto, { color: faixa.cor }]}>{faixa.rotulo}</Text></View>
        </View>
        <View style={styles.metricas}>
          <View style={styles.metrica}><Text style={styles.rotulo}>Estoque hoje</Text><Text style={styles.valor}>{formatarKg(item.estoqueAtualG)}</Text></View>
          <View style={styles.metrica}><Text style={styles.rotulo}>Demanda prevista</Text><Text style={styles.valor}>{formatarKg(item.demandaMediaMensalG)}</Text></View>
        </View>
        <View style={styles.barraFundo}><View style={[styles.barra, { backgroundColor: faixa.cor, width: largura }]} /></View>
        <Text style={[styles.percentual, { color: faixa.cor }]}>{item.coberturaPct.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</Text>
        <View style={styles.sugestao}>
          <Ionicons name="cart-outline" size={20} color="#ffffff" />
          <Text style={styles.sugestaoTexto}>{item.quantidadeSugeridaG > 0 ? `Sugestão de compra: ${formatarCompra(item.quantidadeSugeridaCompra, item.unidadeExibicao)}` : 'Não é necessário comprar para a previsão atual'}</Text>
        </View>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <FlatList
        data={itens}
        keyExtractor={(item) => String(item.ingredienteId)}
        renderItem={renderItem}
        refreshControl={<RefreshControl refreshing={atualizando} onRefresh={atualizar} tintColor="#6c63ff" />}
        contentContainerStyle={styles.lista}
        ListHeaderComponent={(
          <>
            <Text style={styles.periodo}>Previsão para o próximo mês — baseada nos últimos 3 meses</Text>
            <View style={styles.resumo}>
              <View style={styles.resumoTopo}><Text style={styles.resumoTitulo}>Resumo de compras</Text></View>
              <View style={styles.contadores}>{filtros.map(([valor, emoji, total, rotulo]) => (
                <TouchableOpacity key={valor} style={[styles.contador, filtro === valor && styles.contadorAtivo]} onPress={() => setFiltro(filtro === valor ? 'TODOS' : valor)}>
                  <Text style={styles.contadorEmoji}>{emoji}</Text><Text style={styles.contadorNumero}>{total}</Text><Text style={styles.contadorRotulo}>{rotulo}</Text>
                </TouchableOpacity>
              ))}</View>
            </View>
            {dados.produtosSemFichaTecnica > 0 ? <Text style={styles.avisoFicha}>{dados.produtosSemFichaTecnica} produto(s) vendido(s) ainda não têm ficha técnica completa.</Text> : null}
            {erro ? <Text style={styles.avisoFicha}>{erro} Os dados anteriores foram mantidos.</Text> : null}
          </>
        )}
        ListEmptyComponent={<View style={styles.centroLista}><Text style={styles.textoSecundario}>Nenhum ingrediente neste filtro.</Text></View>}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f23' }, lista: { padding: 16 }, centro: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: '#0f0f23' }, centroLista: { alignItems: 'center', paddingVertical: 48 }, textoSecundario: { color: '#8b8ba7', marginTop: 10, textAlign: 'center' }, tituloVazio: { color: '#fff', fontSize: 17, fontWeight: 'bold', marginTop: 12 }, textoErro: { color: '#b8b8cc', marginTop: 8, textAlign: 'center' }, botaoRepetir: { backgroundColor: '#6c63ff', borderRadius: 8, marginTop: 18, paddingHorizontal: 18, paddingVertical: 10 }, botaoRepetirTexto: { color: '#fff', fontWeight: 'bold' },
  periodo: { color: '#b8b8cc', fontSize: 13, lineHeight: 18, marginBottom: 12 }, resumo: { backgroundColor: '#18182e', borderColor: '#2b2b50', borderRadius: 14, borderWidth: 1, padding: 14, marginBottom: 14 }, resumoTopo: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, resumoTitulo: { color: '#fff', fontSize: 16, fontWeight: 'bold' }, como: { color: '#8c84ff', fontSize: 12, fontWeight: 'bold' }, contadores: { flexDirection: 'row', marginTop: 12 }, contador: { alignItems: 'center', borderRadius: 10, flex: 1, paddingVertical: 7 }, contadorAtivo: { backgroundColor: '#292953' }, contadorEmoji: { fontSize: 15 }, contadorNumero: { color: '#fff', fontSize: 17, fontWeight: 'bold' }, contadorRotulo: { color: '#9a9ab3', fontSize: 9 },
  avisoFicha: { backgroundColor: 'rgba(255,183,77,0.12)', borderRadius: 8, color: '#ffd29a', fontSize: 12, lineHeight: 17, marginBottom: 12, padding: 10 }, card: { backgroundColor: '#18182e', borderColor: '#262646', borderRadius: 14, borderWidth: 1, marginBottom: 12, padding: 16 }, cardTopo: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' }, nome: { color: '#fff', flex: 1, fontSize: 16, fontWeight: 'bold' }, badge: { alignItems: 'center', borderRadius: 12, borderWidth: 1, flexDirection: 'row', paddingHorizontal: 9, paddingVertical: 4 }, badgeTexto: { fontSize: 11, fontWeight: 'bold', marginLeft: 4 }, metricas: { flexDirection: 'row', marginTop: 16 }, metrica: { flex: 1 }, rotulo: { color: '#8b8ba7', fontSize: 10 }, valor: { color: '#e5e5ff', fontSize: 13, fontWeight: '600', marginTop: 3 }, barraFundo: { backgroundColor: '#30304e', borderRadius: 5, height: 9, marginTop: 16, overflow: 'hidden' }, barra: { borderRadius: 5, height: 9 }, percentual: { fontSize: 11, fontWeight: 'bold', marginTop: 5, textAlign: 'center' }, sugestao: { alignItems: 'center', backgroundColor: '#292953', borderRadius: 9, flexDirection: 'row', marginTop: 12, padding: 10 }, sugestaoTexto: { color: '#fff', flex: 1, fontSize: 12, fontWeight: '600', marginLeft: 8 }, insuficiente: { alignItems: 'center', flexDirection: 'row', marginTop: 13 }, insuficienteTexto: { color: '#c7c7d8', fontSize: 13, marginLeft: 7 }, motivo: { color: '#85859e', fontSize: 11, marginTop: 5 },
  modalFundo: { alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.72)', flex: 1, justifyContent: 'center', padding: 24 }, modalCard: { backgroundColor: '#1b1b34', borderColor: '#353566', borderRadius: 16, borderWidth: 1, maxWidth: 430, padding: 20, width: '100%' }, modalTitulo: { color: '#fff', fontSize: 19, fontWeight: 'bold', marginBottom: 8 }, modalTexto: { color: '#c7c7d8', fontSize: 14, lineHeight: 21, marginTop: 8 }, botaoFechar: { alignItems: 'center', backgroundColor: '#6c63ff', borderRadius: 9, marginTop: 20, paddingVertical: 11 }, botaoFecharTexto: { color: '#fff', fontWeight: 'bold' },
});
