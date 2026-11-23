import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const cores = {
  TODOS: '#6c63ff',
  CRITICO: '#ff5252',
  ATENCAO: '#ffb74d',
  SAUDAVEL: '#00e676',
};

const categoriasSemaforo = [
  { valor: 'CRITICO', rotulo: 'Críticos', descricao: 'críticos' },
  { valor: 'ATENCAO', rotulo: 'Atenção', descricao: 'em atenção' },
];
const saudavel = { valor: 'SAUDAVEL', rotulo: 'Saudáveis', descricao: 'saudáveis' };

export function SemaforoCriticidade({ contagens, incluirSaudaveis = false }) {
  const categorias = incluirSaudaveis ? [...categoriasSemaforo, saudavel] : categoriasSemaforo;
  const descricao = categorias.map(({ valor, descricao }) => `${contagens[valor]} ${descricao}`).join(', ');
  return (
    <View style={styles.semaforo} accessibilityLabel={descricao}>
      {categorias.map(({ valor, rotulo }) => (
        <View key={valor} style={styles.semaforoItem}>
          <View style={[styles.semaforoPonto, { backgroundColor: cores[valor] }]} />
          <Text style={styles.semaforoTexto}>{rotulo}: {contagens[valor]}</Text>
        </View>
      ))}
    </View>
  );
}

export default function FiltrosCriticidade({ opcoes, selecionado, onSelecionar }) {
  return (
    <View style={styles.linha}>
      {opcoes.map(({ valor, rotulo, texto }) => {
        const ativo = valor === selecionado;
        const cor = cores[valor];
        return (
          <TouchableOpacity
            key={valor}
            style={[styles.botao, { borderColor: cor }, ativo && { backgroundColor: `${cor}33` }]}
            onPress={() => onSelecionar(valor)}
            accessibilityRole="button"
            accessibilityLabel={`Filtrar ${rotulo}`}
            accessibilityState={{ selected: ativo }}
          >
            <Text
              numberOfLines={1}
              adjustsFontSizeToFit
              minimumFontScale={0.85}
              style={[styles.texto, ativo && styles.textoAtivo]}
            >
              {texto}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  semaforo: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 12,
    paddingTop: 12,
    paddingBottom: 2,
    paddingHorizontal: 8,
    backgroundColor: '#14142b',
  },
  semaforoItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  semaforoPonto: { width: 8, height: 8, borderRadius: 4 },
  semaforoTexto: { color: '#c5c5d8', fontSize: 11 },
  linha: {
    flexDirection: 'row',
    paddingHorizontal: 8,
    paddingVertical: 12,
    backgroundColor: '#14142b',
    borderBottomWidth: 1,
    borderBottomColor: '#252545',
  },
  botao: {
    flex: 1,
    alignItems: 'center',
    paddingHorizontal: 4,
    minHeight: 44,
    justifyContent: 'center',
    borderRadius: 20,
    backgroundColor: '#1f1f3a',
    marginHorizontal: 2,
    borderWidth: 1,
  },
  texto: {
    color: '#8b8ba7',
    fontSize: 12,
    textAlign: 'center',
    fontWeight: '500',
  },
  textoAtivo: {
    color: '#ffffff',
    fontWeight: 'bold',
  },
});
