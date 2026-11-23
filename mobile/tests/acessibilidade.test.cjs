const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

function propriedade(tela, estilo, nome) {
  const fonte = fs.readFileSync(path.join(__dirname, '..', 'src', tela), 'utf8');
  const bloco = fonte.match(new RegExp(`\\b${estilo}\\s*[:=]\\s*\\{([^}]*)\\}`));
  assert.ok(bloco, `${tela}: estilo ${estilo} não encontrado`);
  const valor = bloco[1].match(new RegExp(`\\b${nome}:\\s*['\"](#[0-9a-fA-F]{3,6})['\"]`));
  assert.ok(valor, `${tela}: ${estilo}.${nome} não encontrado`);
  return valor[1];
}

function luminancia(hex) {
  const valor = hex.slice(1);
  const bytes = valor.length === 3
    ? [...valor].map((canal) => parseInt(canal + canal, 16))
    : valor.match(/../g).map((canal) => parseInt(canal, 16));
  const [r, g, b] = bytes.map((byte) => {
    const canal = byte / 255;
    return canal <= 0.04045 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4;
  });
  return r * 0.2126 + g * 0.7152 + b * 0.0722;
}

function contraste(frente, fundo) {
  const valores = [luminancia(frente), luminancia(fundo)].sort((a, b) => b - a);
  return (valores[0] + 0.05) / (valores[1] + 0.05);
}

const pares = [
  ['components/FiltrosCriticidade.js', 'texto', 'botao'],
  ['screens/HomeScreen.js', 'comparacao', 'card'],
  ['screens/HomeScreen.js', 'impactoRotulo', 'card'],
  ['screens/HomeScreen.js', 'dia', 'graficoCard'],
  ['screens/HomeScreen.js', 'syncRotulo', 'syncCard'],
  ['screens/HomeScreen.js', 'syncBotaoTexto', 'syncBotao'],
  ['screens/AlertasScreen.js', 'botaoTexto', 'salvar'],
  ['screens/RankingScreen.js', 'comparacao', 'card'],
  ['screens/BcgScreen.js', 'secaoVazia', 'cardVazio'],
  ['screens/PerfilScreen.js', 'version', 'container'],
  ['screens/PromocoesScreen.js', 'botaoTexto', 'botao'],
];

for (const [tela, texto, fundo] of pares) {
  test(`${tela}: ${texto} sobre ${fundo} tem contraste de texto AA`, () => {
    const corTexto = propriedade(tela, texto, 'color');
    const corFundo = propriedade(tela, fundo, 'backgroundColor');
    const razao = contraste(corTexto, corFundo);
    assert.ok(razao >= 4.5, `${corTexto} sobre ${corFundo}: ${razao.toFixed(2)}:1`);
  });
}

test('filtros de criticidade mantêm contraste AA em todos os preenchimentos ativos', () => {
  const componente = 'components/FiltrosCriticidade.js';
  const texto = propriedade(componente, 'textoAtivo', 'color');
  const base = propriedade(componente, 'linha', 'backgroundColor');
  for (const filtro of ['TODOS', 'CRITICO', 'ATENCAO', 'SAUDAVEL']) {
    const cor = propriedade(componente, 'cores', filtro);
    const fundo = '#' + [1, 3, 5].map((i) => {
      const principal = parseInt(cor.slice(i, i + 2), 16);
      const baseCanal = parseInt(base.slice(i, i + 2), 16);
      return Math.round(0.2 * principal + 0.8 * baseCanal).toString(16).padStart(2, '0');
    }).join('');
    assert.ok(contraste(texto, fundo) >= 4.5, `${filtro}: ${texto} sobre ${fundo}`);
  }
});
