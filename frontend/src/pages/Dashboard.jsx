import { useEffect, useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import api from '../services/api';
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer,
    BarChart, Bar, LabelList
} from 'recharts';
import {
    Box, Grid, Card, CardContent, Typography, CircularProgress,
    Alert, AlertTitle, useTheme, Avatar, Button, Divider, Chip
} from '@mui/material';
import {
    Payments, TrendingUp, Warning, LocalOffer, Timeline, WavingHand,
    MoneyOff, HourglassEmpty, Check, Close, Lightbulb, VerifiedRounded
} from '@mui/icons-material';
import TabelaBaixoGiro from '../components/TabelaBaixoGiro';
import Rentabilidade from '../components/Rentabilidade/Rentabilidade';

// Altura fixa da seção principal de conteúdo (coluna esquerda e direita)
// Isso garante que as duas colunas tenham EXATAMENTE a mesma altura total
const ALTURA_COLUNA = 820;

export default function Dashboard() {
    const { user } = useAuth();
    const theme = useTheme();

    // --- ESTADOS ---
    // kpis não é utilizado na renderização, mantido comentado para referência
    // const [kpis, setKpis] = useState(null);

    // Dados mockados curados para os gráficos e tabela de baixo giro (Storytelling perfeito de vendas)
    const [graficos, setGraficos] = useState({
        produtosBaixoGiro: [
            { id: 104, nome: "Salmão Grelhado ao Molho de Ervas", totalVendido: 0, receitaTotal: 0.0 },
            { id: 108, nome: "Mousse de Limão", totalVendido: 1, receitaTotal: 15.0 },
            { id: 112, nome: "Suco de Uva Integral (Copo)", totalVendido: 3, receitaTotal: 24.0 }
        ],
        top5Produtos: [
            { nome: "Risoto de Camarão", totalVendido: 48 },
            { nome: "Filé Mignon ao Molho Madeira", totalVendido: 36 },
            { nome: "Fettuccine Alfredo", totalVendido: 29 },
            { nome: "Petit Gâteau", totalVendido: 25 },
            { nome: "Pizza Margherita", totalVendido: 20 }
        ]
    });

    // Lotes sob alertas simulados com valores redondos e fáceis de apresentar na banca
    const [alertas, setAlertas] = useState([
        {
            id: 1,
            mensagem: "Lote de Carne de Sol expira em 24h",
            dataAlerta: "2026-06-07T10:00:00",
            lote: {
                ingrediente: { nome: "Carne de Sol" },
                quantidade: 3.5,
                custoUnitario: 42.0
            },
            visualizado: false
        },
        {
            id: 2,
            mensagem: "Lote de Creme de Leite Fresco expira em 48h",
            dataAlerta: "2026-06-07T11:00:00",
            lote: {
                ingrediente: { nome: "Creme de Leite Fresco" },
                quantidade: 4.0,
                custoUnitario: 18.5
            },
            visualizado: false
        },
        {
            id: 3,
            mensagem: "Lote de Queijo Muçarela expira em 72h",
            dataAlerta: "2026-06-07T12:00:00",
            lote: {
                ingrediente: { nome: "Queijo Muçarela" },
                quantidade: 2.0,
                custoUnitario: 35.0
            },
            visualizado: false
        }
    ]);

    // Promoções recomendadas mockadas, alinhadas aos alertas de lotes simulados
    const [promocoes, setPromocoes] = useState([
        {
            id: 201,
            produto: { nome: "Risoto de Carne de Sol", preco: 45.0 },
            descontoPct: 30,
            motivo: "Evitar desperdício de 3.5kg de Carne de Sol em risco"
        },
        {
            id: 202,
            produto: { nome: "Fettuccine Alfredo", preco: 38.0 },
            descontoPct: 20,
            motivo: "Aproveitar lote de Creme de Leite Fresco próximo do vencimento"
        },
        {
            id: 203,
            produto: { nome: "Pizza Margherita", preco: 32.0 },
            descontoPct: 15,
            motivo: "Utilizar lote de Queijo Muçarela com vencimento próximo"
        }
    ]);

    // Dados de desperdício do mês atual (Filé Mignon e Camarão concentram as perdas simuladas)
    const [desperdicioMes, setDesperdicioMes] = useState([
        { ingrediente: "Filé Mignon", valorPerdidoRs: 420 },
        { ingrediente: "Camarão Fresco", valorPerdidoRs: 230 },
        { ingrediente: "Outros", valorPerdidoRs: 45 }
    ]);

    // Curva decrescente do histórico de desperdício em 6 meses mostrando impacto do LucroPlus
    const [historico, setHistorico] = useState([
        { mesAno: "Jan/26", valorPerdidoRs: 1150 },
        { mesAno: "Fev/26", valorPerdidoRs: 980 },
        { mesAno: "Mar/26", valorPerdidoRs: 720 },
        { mesAno: "Abr/26", valorPerdidoRs: 450 },
        { mesAno: "Mai/26", valorPerdidoRs: 280 },
        { mesAno: "Jun/26", valorPerdidoRs: 150 }
    ]);

    const [loading, setLoading] = useState(true);

    // --- CARREGAMENTO DE DADOS REAIS DO BACKEND KTOR ---
    useEffect(() => {
        const carregarDashboard = async () => {
            try {
                setLoading(true);
                const [vendasRes, alertasRes, promocoesRes, desperdicioRes, historicoRes] = await Promise.allSettled([
                    api.get('/dashboard/vendas'),
                    api.get('/alertas/vencimento'),
                    api.get('/promocoes/sugestoes'),
                    api.get('/relatorios/desperdicio'),
                    api.get('/relatorios/historico')
                ]);

                if (vendasRes.status === 'fulfilled' && vendasRes.value?.data) {
                    setGraficos(vendasRes.value.data);
                }
                if (alertasRes.status === 'fulfilled' && Array.isArray(alertasRes.value?.data)) {
                    setAlertas(alertasRes.value.data);
                }
                if (promocoesRes.status === 'fulfilled' && Array.isArray(promocoesRes.value?.data)) {
                    setPromocoes(promocoesRes.value.data);
                }
                if (desperdicioRes.status === 'fulfilled' && Array.isArray(desperdicioRes.value?.data)) {
                    setDesperdicioMes(desperdicioRes.value.data);
                }
                if (historicoRes.status === 'fulfilled' && Array.isArray(historicoRes.value?.data)) {
                    setHistorico(historicoRes.value.data);
                }
            } catch (error) {
                console.error("Erro ao carregar dashboard:", error);
            } finally {
                setLoading(false);
            }
        };
        carregarDashboard();
    }, []);

    // --- FORMATADORES ---
    const formatarMoeda = (valor) => {
        return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor || 0);
    };

    // --- AÇÕES RÁPIDAS DE PROMOÇÃO CONECTADAS À API KTOR ---
    const ativarPromocao = async (id) => {
        try {
            await api.patch(`/promocoes/${id}/ativar`);
        } catch (error) {
            console.error('Erro ao ativar promoção:', error);
        }
        setPromocoes(prev => prev.filter(p => p.id !== id));
    };

    const recusarPromocao = async (id) => {
        try {
            await api.patch(`/promocoes/${id}/recusar`);
        } catch (error) {
            console.error('Erro ao recusar promoção:', error);
        }
        setPromocoes(prev => prev.filter(p => p.id !== id));
    };

    // --- TELA DE LOADING ---
    if (loading) {
        return (
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '60vh' }}>
                <CircularProgress size={60} thickness={4} sx={{ mb: 2 }} />
                <Typography variant="body1" color="text.secondary" fontStyle="italic">
                    Carregando inteligência de validade...
                </Typography>
            </Box>
        );
    }

    // --- CÁLCULOS REATIVOS (executados após o loading) ---

    // Capital em Risco = soma de (quantidade * custo unitário) de cada lote em alerta
    const totalCapitalEmRisco = alertas.reduce((acc, curr) => {
        if (curr.lote) {
            return acc + ((curr.lote.quantidade || 0) * (curr.lote.custoUnitario || 0));
        }
        return acc;
    }, 0);

    // Desperdício real acumulado no mês atual
    const totalDesperdicioMes = desperdicioMes.reduce((acc, curr) => acc + (curr.valorPerdidoRs || 0), 0);

    // Estimativa de prejuízo evitado com base em produtos escoados via promoções
    const totalEvitado = (graficos?.top5Produtos?.reduce((acc, curr) => acc + (curr.totalVendido || 0), 0) || 0) * 4.25;

    // Agrupa alertas por ingrediente para o gráfico de barras
    const ingredienteRiscoMap = {};
    alertas.forEach(a => {
        if (a.lote && a.lote.ingrediente) {
            const nome = a.lote.ingrediente.nome;
            const valor = (a.lote.quantidade || 0) * (a.lote.custoUnitario || 0);
            ingredienteRiscoMap[nome] = (ingredienteRiscoMap[nome] || 0) + valor;
        }
    });
    const riskData = Object.entries(ingredienteRiscoMap)
        .map(([name, value]) => ({ nome: name, valorRisco: value }))
        .sort((a, b) => b.valorRisco - a.valorRisco)
        .slice(0, 5);

    // --- CARDS DE KPIs ---
    const kpiCards = [
        {
            title: 'Lotes sob Alerta',
            value: `${alertas.length} Lotes`,
            icon: <HourglassEmpty />,
            color: '#FF9800',
            // Se não há alertas, a mensagem de rodapé muda para tranquilizadora
            desc: alertas.length === 0 ? '✓ Tudo seguro' : 'Validade em até 72h'
        },
        {
            title: 'Capital em Risco',
            value: formatarMoeda(totalCapitalEmRisco),
            icon: <Payments />,
            color: '#004AF7',
            desc: totalCapitalEmRisco === 0 ? 'Sem perdas projetadas' : 'Insumos em risco imediato'
        },
        {
            title: 'Perda por Vencimento',
            value: formatarMoeda(totalDesperdicioMes),
            icon: <MoneyOff />,
            color: '#F44336',
            desc: totalDesperdicioMes === 0 ? 'Cozinha 100% eficiente' : 'Desperdício real acumulado'
        },
        {
            title: 'Prejuízo Evitado',
            value: formatarMoeda(totalEvitado),
            icon: <TrendingUp />,
            color: '#4CAF50',
            desc: 'Recuperado via Promoções'
        },
    ];

    return (
        <Box sx={{ pb: 4 }}>

            {/* =====================================================
                SEÇÃO 1: CABEÇALHO E BANNERS DE NOTIFICAÇÃO
            ===================================================== */}
            <Box sx={{ mb: 3 }}>
                <Typography variant="h4" fontWeight="bold" gutterBottom sx={{ display: 'flex', alignItems: 'center', gap: 1.5, color: 'primary.dark' }}>
                    Painel de Validade e Prevenção <WavingHand sx={{ color: '#FF9800', fontSize: 32 }} />
                </Typography>
                <Typography variant="subtitle1" color="text.secondary">
                    Monitore a validade dos seus lotes, evite perdas e otimize o cardápio.
                </Typography>
            </Box>

            {/* Banners — só aparecem se houver alertas ou sugestões */}
            {(alertas.length > 0 || promocoes.length > 0) && (
                <Grid container spacing={2} sx={{ mb: 3 }}>
                    {alertas.length > 0 && (
                        <Grid item xs={12} md={promocoes.length > 0 ? 6 : 12}>
                            <Alert severity="warning" icon={<Warning fontSize="inherit" />} sx={{ borderRadius: 2 }}>
                                <AlertTitle><strong>Atenção: Validades Próximas</strong></AlertTitle>
                                {alertas.length} lote(s) a expirar em até 72h. Priorize o consumo ou lance descontos.
                            </Alert>
                        </Grid>
                    )}
                    {promocoes.length > 0 && (
                        <Grid item xs={12} md={alertas.length > 0 ? 6 : 12}>
                            <Alert severity="info" icon={<LocalOffer fontSize="inherit" />} sx={{ borderRadius: 2 }}>
                                <AlertTitle><strong>Sugestões de Promoção Ativas</strong></AlertTitle>
                                O Motor LucroPlus gerou {promocoes.length} sugestão(ões) de desconto para escoar estoque.
                            </Alert>
                        </Grid>
                    )}
                </Grid>
            )}

            {/* =====================================================
                SEÇÃO 2: 4 KPI CARDS (linha equilíbrada)
            ===================================================== */}
            <Grid container spacing={3} sx={{ mb: 4 }}>
                {kpiCards.map((kpi, index) => (
                    <Grid item xs={12} sm={6} md={3} key={index}>
                        <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', display: 'flex', alignItems: 'center', p: 2, height: '100%' }}>
                            <Avatar sx={{ bgcolor: kpi.color + '18', color: kpi.color, width: 56, height: 56, mr: 2 }}>
                                {kpi.icon}
                            </Avatar>
                            <Box>
                                <Typography variant="caption" color="text.secondary" fontWeight="bold">
                                    {kpi.title}
                                </Typography>
                                <Typography variant="h5" fontWeight="bold" color="text.primary" sx={{ my: 0.5 }}>
                                    {kpi.value}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" display="block">
                                    {kpi.desc}
                                </Typography>
                            </Box>
                        </Card>
                    </Grid>
                ))}
            </Grid>

            {/* =====================================================
                SEÇÃO 3: DUAS COLUNAS ASSIMÉTRICAS (7 / 5)
                Coluna Esquerda (lg=7): Painel de Ação
                Coluna Direita  (lg=5): Painel de Decisões
                Ambas as colunas têm altura FIXA = ALTURA_COLUNA
                para garantir alinhamento perfeito no desktop.
            ===================================================== */}
            <Grid container spacing={3} alignItems="stretch">

                {/* -----------------------------------------------
                    COLUNA ESQUERDA (7/12): Painel de Ação
                    Contém:
                      1. Gráfico de Área — Histórico de Desperdício
                      2. Tabela Baixo Giro
                ----------------------------------------------- */}
                <Grid item xs={12} lg={7} sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>

                    {/* Card 1: Gráfico de Histórico de Desperdício (Últimos 6 Meses) */}
                    <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: 370, display: 'flex', flexDirection: 'column' }}>
                        <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                                <Timeline color="primary" sx={{ mr: 1 }} />
                                <Typography variant="h6" fontWeight="bold">Histórico de Desperdício (6 Meses)</Typography>
                            </Box>
                            <Box sx={{ flexGrow: 1, minHeight: 0 }}>
                                {historico.length > 0 ? (
                                    // Gráfico de Área do Recharts — elegante e simples
                                    <ResponsiveContainer width="99%" height="100%">
                                        <AreaChart data={historico} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
                                            <defs>
                                                {/* Gradiente da cor vermelha transparecendo para baixo */}
                                                <linearGradient id="gradientPerdas" x1="0" y1="0" x2="0" y2="1">
                                                    <stop offset="5%" stopColor="#F44336" stopOpacity={0.3} />
                                                    <stop offset="95%" stopColor="#F44336" stopOpacity={0.0} />
                                                </linearGradient>
                                            </defs>
                                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(0,0,0,0.05)" />
                                            <XAxis dataKey="mesAno" tick={{ fontSize: 12 }} />
                                            <YAxis tickFormatter={(v) => `R$${v}`} tick={{ fontSize: 11 }} />
                                            <RechartsTooltip formatter={(v) => [formatarMoeda(v), "Valor Perdido"]} />
                                            <Area type="monotone" dataKey="valorPerdidoRs" stroke="#F44336" strokeWidth={3} fillOpacity={1} fill="url(#gradientPerdas)" />
                                        </AreaChart>
                                    </ResponsiveContainer>
                                ) : (
                                    // Placeholder elegante quando não há histórico de perdas
                                    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1, p: 3, textAlign: 'center' }}>
                                        <VerifiedRounded sx={{ fontSize: 48, color: 'success.main' }} />
                                        <Typography variant="body1" fontWeight="bold" color="success.main">
                                            Parabéns!
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            Sua cozinha não registrou perdas físicas nos últimos meses.
                                        </Typography>
                                    </Box>
                                )}
                            </Box>
                        </CardContent>
                    </Card>

                    {/* Card 2: Tabela Baixo Giro — altura flexível para preencher o resto da coluna */}
                    <Box sx={{ flexGrow: 1 }}>
                        <TabelaBaixoGiro dados={graficos?.produtosBaixoGiro} formatarMoeda={formatarMoeda} />
                    </Box>
                </Grid>

                {/* -----------------------------------------------
                    COLUNA DIREITA (5/12): Painel de Decisões
                    Contém:
                      1. Central de Promoções Recomendadas
                      2. Rentabilidade por Produto
                ----------------------------------------------- */}
                <Grid item xs={12} lg={5} sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>

                    {/* Card 3: Central de Aprovação Rápida de Promoções */}
                    <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: 370, display: 'flex', flexDirection: 'column' }}>
                        <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1.5 }}>
                                <LocalOffer color="primary" sx={{ mr: 1, fontSize: 28 }} />
                                <Box>
                                    <Typography variant="h6" fontWeight="bold">Promoções Recomendadas</Typography>
                                    <Typography variant="caption" color="text.secondary">Descontos sugeridos para escoar o estoque.</Typography>
                                </Box>
                            </Box>
                            <Divider sx={{ mb: 1.5 }} />

                            {/* Área com scroll interno para a lista de promoções */}
                            <Box sx={{ flexGrow: 1, overflowY: 'auto', pr: 0.5 }}>
                                {promocoes.length === 0 ? (
                                    // Estado vazio: mensagem motivadora
                                    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', p: 2 }}>
                                        <Check sx={{ fontSize: 44, color: 'success.main', mb: 1 }} />
                                        <Typography variant="body1" fontWeight="bold" color="success.main">
                                            Tudo Sob Controle!
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            Nenhum lote com risco iminente. Inventário saudável!
                                        </Typography>
                                    </Box>
                                ) : (
                                    // Lista de promoções com layout reorganizado para hierarquia visual clara
                                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                                        {promocoes.map(promo => {
                                            // Calcula o preço final após o desconto
                                            const original = promo.produto.preco;
                                            const comDesconto = original * (1 - promo.descontoPct / 100);
                                            return (
                                                // Card de promoção: borda lateral azul sinaliza que é uma sugestão do sistema
                                                <Card key={promo.id} variant="outlined" sx={{
                                                    borderRadius: 2,
                                                    p: 1.5,
                                                    borderLeft: '4px solid #004AF7',
                                                    transition: 'box-shadow 0.2s',
                                                    '&:hover': { boxShadow: '0 2px 12px rgba(0,74,247,0.1)' }
                                                }}>
                                                    {/* LINHA 1: Nome do produto + chip de desconto na mesma linha */}
                                                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.8 }}>
                                                        <Typography variant="body2" fontWeight="bold" sx={{ flexGrow: 1, mr: 1 }}>
                                                            {promo.produto.nome}
                                                        </Typography>
                                                        <Chip
                                                            label={`${promo.descontoPct}% OFF`}
                                                            size="small"
                                                            color="primary"
                                                            sx={{ fontWeight: 'bold', fontSize: 10, flexShrink: 0 }}
                                                        />
                                                    </Box>

                                                    {/* LINHA 2: Motivo da sugestão — fundo laranja ultra-suave com borda fina */}
                                                    <Box sx={{
                                                        display: 'flex',
                                                        alignItems: 'flex-start',
                                                        bgcolor: 'rgba(255, 152, 0, 0.06)',
                                                        border: '1px solid rgba(255, 152, 0, 0.2)',
                                                        p: 0.8,
                                                        borderRadius: 1,
                                                        mb: 1
                                                    }}>
                                                        <Lightbulb color="warning" sx={{ mr: 0.8, fontSize: 14, mt: 0.1, flexShrink: 0 }} />
                                                        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.4 }}>
                                                            {promo.motivo}
                                                        </Typography>
                                                    </Box>

                                                    {/* LINHA 3: Preços (riscado → com desconto) + botões de ação */}
                                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                        {/* Seção de preços: preço original riscado e novo preço em verde */}
                                                        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.8 }}>
                                                            <Typography variant="caption" color="text.disabled" sx={{ textDecoration: 'line-through' }}>
                                                                R$ {original.toFixed(2)}
                                                            </Typography>
                                                            <Typography variant="body2" fontWeight="bold" color="success.main">
                                                                R$ {comDesconto.toFixed(2)}
                                                            </Typography>
                                                        </Box>

                                                        {/* Botões de ação: [X] Recusar e [Ativar] com tamanho confortável */}
                                                        <Box sx={{ display: 'flex', gap: 1 }}>
                                                            <Button
                                                                size="small"
                                                                variant="outlined"
                                                                color="inherit"
                                                                onClick={() => recusarPromocao(promo.id)}
                                                                sx={{ minWidth: 36, p: '4px 8px', borderRadius: 1.5 }}
                                                            >
                                                                <Close fontSize="small" />
                                                            </Button>
                                                            <Button
                                                                size="small"
                                                                variant="contained"
                                                                color="primary"
                                                                onClick={() => ativarPromocao(promo.id)}
                                                                sx={{ borderRadius: 1.5, textTransform: 'none', fontWeight: 'bold', px: 2 }}
                                                            >
                                                                Ativar
                                                            </Button>
                                                        </Box>
                                                    </Box>
                                                </Card>
                                            );
                                        })}
                                    </Box>
                                )}
                            </Box>
                        </CardContent>
                    </Card>

                    {/* Card 4: Rentabilidade — flexível para preencher o restante da coluna */}
                    <Box sx={{ flexGrow: 1 }}>
                        <Rentabilidade />
                    </Box>
                </Grid>

            </Grid>
        </Box>
    );
}


