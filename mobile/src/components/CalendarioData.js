import React, { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const iso = (data) => `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;

export default function CalendarioData({ visivel, valor, minimo, maximo, onSelect, onClose }) {
  const [mes, setMes] = useState(() => {
    const base = valor || minimo || iso(new Date());
    return new Date(`${base.slice(0, 7)}-01T12:00:00`);
  });
  useEffect(() => {
    if (visivel) {
      const base = valor || minimo || iso(new Date());
      setMes(new Date(`${base.slice(0, 7)}-01T12:00:00`));
    }
  }, [visivel, valor, minimo]);
  const primeiroDia = new Date(mes.getFullYear(), mes.getMonth(), 1).getDay();
  const dias = new Date(mes.getFullYear(), mes.getMonth() + 1, 0).getDate();
  const nomeMes = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(mes);
  const mudarMes = (delta) => setMes(new Date(mes.getFullYear(), mes.getMonth() + delta, 1));
  return <Modal visible={visivel} transparent animationType="fade" onRequestClose={onClose}>
    <View style={styles.fundo}><View style={styles.cartao}>
      <View style={styles.cabecalho}>
        <Pressable onPress={() => mudarMes(-1)} accessibilityRole="button" accessibilityLabel="Mês anterior"><Text style={styles.seta}>‹</Text></Pressable>
        <Text style={styles.titulo}>{nomeMes.charAt(0).toUpperCase() + nomeMes.slice(1)}</Text>
        <Pressable onPress={() => mudarMes(1)} accessibilityRole="button" accessibilityLabel="Próximo mês"><Text style={styles.seta}>›</Text></Pressable>
      </View>
      <View style={styles.grade}>{['D', 'S', 'T', 'Q', 'Q', 'S', 'S'].map((dia, i) => <Text key={`semana-${i}`} style={styles.diaSemana}>{dia}</Text>)}
        {Array.from({ length: primeiroDia }, (_, i) => <View key={`vazio-${i}`} style={styles.dia} />)}
        {Array.from({ length: dias }, (_, i) => {
          const data = iso(new Date(mes.getFullYear(), mes.getMonth(), i + 1));
          const fora = (minimo && data < minimo) || (maximo && data > maximo);
          return <TouchableOpacity key={data} disabled={fora} onPress={() => { onSelect(data); onClose(); }} style={[styles.dia, data === valor && styles.selecionado]} accessibilityRole="button" accessibilityLabel={new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(`${data}T12:00:00`))}>
            <Text style={[styles.diaTexto, fora && styles.desabilitado]}>{i + 1}</Text>
          </TouchableOpacity>;
        })}</View>
      <TouchableOpacity onPress={onClose} style={styles.fechar} accessibilityRole="button"><Text style={styles.fecharTexto}>Cancelar</Text></TouchableOpacity>
    </View></View>
  </Modal>;
}

const styles = StyleSheet.create({
  fundo: { flex: 1, backgroundColor: '#000b', justifyContent: 'center', padding: 20 },
  cartao: { backgroundColor: '#20203c', borderRadius: 16, padding: 16 },
  cabecalho: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 15 },
  titulo: { color: '#fff', fontWeight: 'bold', fontSize: 16 }, seta: { color: '#fff', fontSize: 28, paddingHorizontal: 12 },
  grade: { flexDirection: 'row', flexWrap: 'wrap' }, diaSemana: { width: '14.28%', textAlign: 'center', color: '#aaaac1', marginBottom: 9 },
  dia: { width: '14.28%', height: 42, justifyContent: 'center', alignItems: 'center', borderRadius: 21 },
  selecionado: { backgroundColor: '#5b50dc' }, diaTexto: { color: '#fff', fontSize: 14 }, desabilitado: { color: '#686882' },
  fechar: { alignSelf: 'flex-end', marginTop: 12, padding: 10 }, fecharTexto: { color: '#bcb7ff', fontWeight: 'bold' },
});
