import { useEffect, useState } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer } from 'recharts';
import api from '../../services/api';
import { 
    Card, CardContent, Typography, Box, Grid, CircularProgress, useTheme 
} from '@mui/material';
import { QueryStats, CalendarToday } from '@mui/icons-material';

export default function PrevisaoDemanda() {
    const theme = useTheme();
    const [dadosPrevisao, setDadosPrevisao] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const carregarPrevisao = async () => {
            try {
                setLoading(true);
                const response = await api.get('/dashboard/previsao');
                if (Array.isArray(response.data) && response.data.length > 0) {
                    setDadosPrevisao(response.data);
                }
            } catch (error) {
                console.error("Erro ao carregar previsão de demanda:", error);
            } finally {
                setLoading(false);
            }
        };

        carregarPrevisao();
    }, []);

    const getNomeDia = (diaSemana) => {
        const dias = { 1: 'Domingo', 2: 'Segunda', 3: 'Terça', 4: 'Quarta', 5: 'Quinta', 6: 'Sexta', 7: 'Sábado' };
        return dias[diaSemana] || '';
    };

    if (loading) {
        return (
            <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', mb: 3, height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 300 }}>
                <CircularProgress />
            </Card>
        );
    }

    const hojeJs = new Date().getDay();
    const amanhaMysql = ((hojeJs + 1) % 7) + 1;
    
    const previsaoAmanha = dadosPrevisao
        .filter(item => item.diaSemana === amanhaMysql)
        .sort((a, b) => b.mediaVendasEsperada - a.mediaVendasEsperada);

    const diasProjecao = [];
    for (let i = 1; i <= 7; i++) {
        const diaMysql = ((hojeJs + i - 1) % 7) + 1;
        const totalEsperado = dadosPrevisao
            .filter(item => item.diaSemana === diaMysql)
            .reduce((sum, curr) => sum + curr.mediaVendasEsperada, 0);

        diasProjecao.push({
            nomeDia: getNomeDia(diaMysql).substring(0, 3), // Abreviação (Seg, Ter)
            mediaVendas: Math.round(totalEsperado)
        });
    }

    return (
        <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: '100%', overflow: 'visible' }}>
            <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                    <QueryStats color="primary" sx={{ mr: 1, fontSize: 32 }} />
                    <Box>
                        <Typography variant="h6" fontWeight="bold">Previsão de Demanda</Typography>
                        <Typography variant="caption" color="text.secondary">Projeção baseada nas últimas 4 semanas.</Typography>
                    </Box>
                </Box>

                <Grid container spacing={4}>
                    <Grid item xs={12}>
                        <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                            <CalendarToday fontSize="small" color="action" />
                            <Typography variant="subtitle2" fontWeight="bold" color="text.secondary">
                                Projeção para {getNomeDia(amanhaMysql)} (Amanhã)
                            </Typography>
                        </Box>
                        
                        <Grid container spacing={2}>
                            {previsaoAmanha.length > 0 ? (
                                previsaoAmanha.map(item => (
                                    <Grid item xs={12} key={item.produtoId}>
                                        <Card variant="outlined" sx={{ borderRadius: 2, p: 1.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center', bgcolor: 'background.default' }}>
                                            <Typography variant="body2" fontWeight="bold">{item.produtoNome}</Typography>
                                            <Typography variant="h6" fontWeight="900" color="primary.main">
                                                {Math.round(item.mediaVendasEsperada)} <Typography component="span" variant="caption" color="text.secondary">unid.</Typography>
                                            </Typography>
                                        </Card>
                                    </Grid>
                                ))
                            ) : (
                                <Grid item xs={12}>
                                    <Typography variant="body2" color="text.secondary">Sem dados históricos para amanhã.</Typography>
                                </Grid>
                            )}
                        </Grid>
                    </Grid>

                    <Grid item xs={12}>
                        <Typography variant="subtitle2" fontWeight="bold" color="text.secondary" sx={{ mb: 2 }}>
                            Volume Total (7 Dias)
                        </Typography>
                        <Box sx={{ height: 350, width: '100%', minWidth: 0 }}>
                            {diasProjecao.length > 0 ? (
                                <ResponsiveContainer width="99%" height="100%">
                                    <LineChart data={diasProjecao}>
                                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(0,0,0,0.05)"/>
                                        <XAxis dataKey="nomeDia" tick={{fill: theme.palette.text.secondary, fontSize: 11}} />
                                        <YAxis tick={{fill: theme.palette.text.secondary, fontSize: 11}} />
                                        <RechartsTooltip 
                                            contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 20px rgba(0,0,0,0.1)' }}
                                            wrapperStyle={{ zIndex: 1000 }}
                                            formatter={(value) => [`${value} unid.`, 'Volume Esperado']}
                                        />
                                        <Line 
                                            type="monotone" 
                                            dataKey="mediaVendas" 
                                            stroke={theme.palette.warning.main} 
                                            strokeWidth={4} 
                                            activeDot={{ r: 8, fill: theme.palette.warning.dark, stroke: '#fff', strokeWidth: 2 }} 
                                        />
                                    </LineChart>
                                </ResponsiveContainer>
                            ) : (
                                <Typography variant="body2" color="text.secondary">Sem dados para gerar gráfico.</Typography>
                            )}
                        </Box>
                    </Grid>
                </Grid>
            </CardContent>
        </Card>
    );
}



