import React, { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import api from '../../services/api';
import { 
    Box, Typography, Grid, Card, CardContent, useTheme, Skeleton 
} from '@mui/material';
import { Analytics, MoneyOff } from '@mui/icons-material';

export default function Relatorios() {
    const theme = useTheme();
    // Dados mockados curados para composição do desperdício no mês atual (consistente com o Dashboard)
    const [desperdicioMes, setDesperdicioMes] = useState([
        { ingrediente: "Filé Mignon", valorPerdidoRs: 420 },
        { ingrediente: "Camarão Fresco", valorPerdidoRs: 230 },
        { ingrediente: "Outros", valorPerdidoRs: 45 }
    ]);

    // Histórico de desperdício decrescente mostrando a eficácia do LucroPlus ao longo de 6 meses
    const [historico, setHistorico] = useState([
        { mesAno: "Jan/26", valorPerdidoRs: 1150 },
        { mesAno: "Fev/26", valorPerdidoRs: 980 },
        { mesAno: "Mar/26", valorPerdidoRs: 720 },
        { mesAno: "Abr/26", valorPerdidoRs: 450 },
        { mesAno: "Mai/26", valorPerdidoRs: 280 },
        { mesAno: "Jun/26", valorPerdidoRs: 150 }
    ]);

    const [loading, setLoading] = useState(true);

    // Cores para o gráfico de pizza usando a paleta do tema (escalas de warning e error)
    const COLORS = [
        theme.palette.error.main, 
        theme.palette.warning.main, 
        theme.palette.error.light, 
        theme.palette.warning.light, 
        theme.palette.error.dark, 
        theme.palette.warning.dark
    ];

    // --- CARREGAMENTO DE DADOS REAIS DO BACKEND KTOR ---
    useEffect(() => {
        const carregarRelatorios = async () => {
            try {
                setLoading(true);
                const [desperdicioRes, historicoRes] = await Promise.allSettled([
                    api.get('/relatorios/desperdicio'),
                    api.get('/relatorios/historico')
                ]);
                
                if (desperdicioRes.status === 'fulfilled' && Array.isArray(desperdicioRes.value?.data)) {
                    setDesperdicioMes(desperdicioRes.value.data);
                }
                if (historicoRes.status === 'fulfilled' && Array.isArray(historicoRes.value?.data)) {
                    setHistorico(historicoRes.value.data);
                }
            } catch (error) {
                console.error("Erro ao carregar relatórios:", error);
            } finally {
                setLoading(false);
            }
        };

        carregarRelatorios();
    }, []);

    const formatarMoeda = (valor) => {
        return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor || 0);
    };

    const totalDesperdicioMes = desperdicioMes.reduce((acc, curr) => acc + curr.valorPerdidoRs, 0);

    if (loading) {
        return (
            <Box sx={{ pb: 4 }}>
                <Box sx={{ mb: 4 }}>
                    <Skeleton width={300} height={40} />
                    <Skeleton width={500} height={20} />
                </Box>
                <Grid container spacing={3}>
                    <Grid item xs={12}>
                        <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 3 }} />
                    </Grid>
                    <Grid item xs={12} md={6}>
                        <Skeleton variant="rectangular" height={400} sx={{ borderRadius: 3 }} />
                    </Grid>
                    <Grid item xs={12} md={6}>
                        <Skeleton variant="rectangular" height={400} sx={{ borderRadius: 3 }} />
                    </Grid>
                </Grid>
            </Box>
        );
    }

    return (
        <Box sx={{ pb: 4 }}>
            <Box sx={{ mb: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
                {/* Ícone do título: fundo neutro suave com ícone escuro — sem repetir vermelho sobre vermelho */}
                <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(0,0,0,0.06)', display: 'flex' }}>
                    <Analytics sx={{ color: 'text.primary', fontSize: 28 }} />
                </Box>
                    {/* Título atualizado: "Histórico" é mais preciso e menos formal que "Relatório" */}
                    <Typography variant="h4" fontWeight="bold" color="text.primary">
                        Histórico de Desperdício
                    </Typography>
                </Box>
                <Typography variant="body1" color="text.secondary">
                    Acompanhe o histórico de perdas financeiras geradas por lotes vencidos e não vendidos.
                </Typography>
            </Box>

            {/* Card de Resumo (Mês Atual) */}
            <Card sx={{ borderRadius: 3, mb: 4, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', bgcolor: 'error.50', border: '1px solid', borderColor: 'error.light' }}>
                <CardContent sx={{ display: 'flex', alignItems: 'center', p: 4 }}>
                    <Box sx={{ p: 2, borderRadius: '50%', bgcolor: 'error.main', display: 'flex', mr: 3 }}>
                        <MoneyOff sx={{ color: '#fff', fontSize: 40 }} />
                    </Box>
                    <Box>
                        <Typography variant="h6" color="error.dark" gutterBottom fontWeight="bold">
                            Perda Total (Mês Atual)
                        </Typography>
                        <Typography variant="h3" fontWeight="900" color="error.main">
                            {formatarMoeda(totalDesperdicioMes)}
                        </Typography>
                    </Box>
                </CardContent>
            </Card>

            <Grid container spacing={4}>
                {/* Gráfico de Pizza — ocupa 5/12 da tela no desktop para ceder espaço ao gráfico de barras */}
                <Grid item xs={12} lg={5}>
                    <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: '100%' }}>
                        <CardContent sx={{ p: 4 }}>
                            <Typography variant="h6" fontWeight="bold" gutterBottom>
                                Composição do Desperdício (Mês Atual)
                            </Typography>
                            <Box sx={{ height: 350, mt: 4 }}>
                                {desperdicioMes.length > 0 ? (
                                    <ResponsiveContainer width="99%" height="100%">
                                        <PieChart>
                                            <Pie
                                                data={desperdicioMes}
                                                dataKey="valorPerdidoRs"
                                                nameKey="ingrediente"
                                                cx="50%"
                                                cy="50%"
                                                outerRadius={100}
                                            >
                                                {desperdicioMes.map((entry, index) => (
                                                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                                ))}
                                            </Pie>
                                            <Tooltip formatter={(value) => formatarMoeda(value)} wrapperStyle={{ zIndex: 1000 }} />
                                            <Legend verticalAlign="bottom" height={36} />
                                        </PieChart>
                                    </ResponsiveContainer>
                                ) : (
                                    <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                        <Typography color="success.main" fontWeight="bold">Nenhum desperdício registrado neste mês. Ótimo trabalho!</Typography>
                                    </Box>
                                )}
                            </Box>
                        </CardContent>
                    </Card>
                </Grid>

                {/* Gráfico de Barras — ocupa 7/12 para ter mais largura horizontal no eixo X */}
                <Grid item xs={12} lg={7}>
                    <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: '100%' }}>
                        <CardContent sx={{ p: 4 }}>
                            <Typography variant="h6" fontWeight="bold" gutterBottom>
                                Histórico de Perdas (Últimos 6 Meses)
                            </Typography>
                            {/* Altura aumentada de 350 para 390 para acomodar as labels do eixo X inclinadas */}
                            <Box sx={{ height: 390, mt: 4 }}>
                                {historico.length > 0 ? (
                                    <ResponsiveContainer width="99%" height="100%">
                                        {/* margin.bottom aumentado de 20 para 45 para acomodar os textos inclinados do eixo X */}
                                        <BarChart data={historico} margin={{ top: 20, right: 20, left: 20, bottom: 45 }}>
                                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={theme.palette.divider} />
                                            {/* angle=-35 e textAnchor="end" inclinam os labels (Jan/26, Fev/26 etc.)
                                                evitando a sobreposição que ocorria com os 6 rótulos no espaço estreito */}
                                            <XAxis
                                                dataKey="mesAno"
                                                tick={{ fill: theme.palette.text.secondary, fontSize: 12 }}
                                                interval={0}
                                                angle={-35}
                                                textAnchor="end"
                                                dy={4}
                                            />
                                            <YAxis tick={{ fill: theme.palette.text.secondary }} tickFormatter={(value) => `R$${value}`} />
                                            <Tooltip 
                                                formatter={(value) => formatarMoeda(value)}
                                                labelFormatter={(label) => `Mês: ${label}`}
                                                wrapperStyle={{ zIndex: 1000 }}
                                            />
                                            {/* barSize fixo para evitar colunas muito largas quando o gráfico é amplo */}
                                            <Bar dataKey="valorPerdidoRs" fill={theme.palette.error.main} radius={[4, 4, 0, 0]} name="Valor Perdido" barSize={38} />
                                        </BarChart>
                                    </ResponsiveContainer>
                                ) : (
                                    <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                        <Typography color="text.secondary">Sem histórico suficiente para exibir.</Typography>
                                    </Box>
                                )}
                            </Box>
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>
        </Box>
    );
}



