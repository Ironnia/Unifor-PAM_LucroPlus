import { useEffect, useState } from 'react';
import api from '../../services/api';
import { 
    Box, Typography, Grid, Card, CardContent, CardActions, 
    Button, Chip, Skeleton, useTheme, Avatar, IconButton 
} from '@mui/material';
import { 
    NotificationsActive, WarningAmber, Error, InfoOutlined, 
    TaskAlt, DoneAll 
} from '@mui/icons-material';

export default function Alertas() {
    const theme = useTheme();
    const [alertas, setAlertas] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        carregarAlertas();
    }, []);

    const carregarAlertas = async () => {
        try {
            setLoading(true);
            const response = await api.get('/alertas/vencimento');
            if (Array.isArray(response.data)) {
                setAlertas(response.data);
            }
        } catch (error) {
            console.error('Erro ao buscar alertas:', error);
        } finally {
            setLoading(false);
        }
    };

    const marcarVisualizado = async (id) => {
        try {
            await api.patch(`/alertas/${id}/visualizar`);
            setAlertas(prev => prev.filter(a => a.id !== id));
        } catch (error) {
            console.error('Erro ao marcar alerta:', error);
        }
    };

    // Define a severidade do alerta com base na urgência e retorna as cores ideais com excelente contraste
    const definirSeveridade = (mensagem) => {
        const msgLower = mensagem.toLowerCase();
        if (msgLower.includes('vence hoje') || msgLower.includes('amanhã') || msgLower.includes('1 dia') || msgLower.includes('2 dias') || msgLower.includes('urgente')) {
            return { 
                cor: 'error', 
                label: 'Crítico', 
                Icon: Error,
                bgIcone: 'rgba(244, 67, 54, 0.12)', // Vermelho translúcido suave
                corIcone: '#F44336' // Vermelho vivo para excelente contraste
            };
        }
        if (msgLower.includes('3 dias') || msgLower.includes('4 dias') || msgLower.includes('5 dias') || msgLower.includes('atenção')) {
            return { 
                cor: 'warning', 
                label: 'Atenção', 
                Icon: WarningAmber,
                bgIcone: 'rgba(255, 152, 0, 0.12)', // Laranja translúcido suave
                corIcone: '#FF9800' // Laranja vivo para excelente contraste
            };
        }
        return { 
            cor: 'info', 
            label: 'Moderado', 
            Icon: InfoOutlined,
            bgIcone: 'rgba(0, 74, 247, 0.12)', // Azul translúcido suave
            corIcone: '#004AF7' // Azul vivo para excelente contraste
        };
    };

    if (loading) {
        return (
            <Box sx={{ pb: 4 }}>
                <Box sx={{ mb: 4 }}>
                    <Skeleton width={300} height={40} />
                    <Skeleton width={500} height={20} />
                </Box>
                <Grid container spacing={3}>
                    {[1, 2, 3].map(n => (
                        <Grid item xs={12} md={6} lg={4} key={n}>
                            <Card sx={{ borderRadius: 3 }}>
                                <CardContent>
                                    <Skeleton variant="circular" width={40} height={40} sx={{ mb: 2 }} />
                                    <Skeleton width="60%" height={24} sx={{ mb: 1 }} />
                                    <Skeleton width="100%" height={20} />
                                    <Skeleton width="80%" height={20} />
                                </CardContent>
                            </Card>
                        </Grid>
                    ))}
                </Grid>
            </Box>
        );
    }

    return (
        <Box sx={{ pb: 4 }}>
            <Box sx={{ mb: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
                    {/* Ícone do título: fundo azul primário ultra-suave com ícone azul — sem repetir vermelho sobre vermelho */}
                    <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(0, 74, 247, 0.08)', display: 'flex' }}>
                        <NotificationsActive sx={{ color: 'primary.main', fontSize: 28 }} />
                    </Box>
                    <Typography variant="h4" fontWeight="bold" color="text.primary">
                        Central de Alertas
                    </Typography>
                </Box>
                <Typography variant="body1" color="text.secondary">
                    Monitore os lotes com vencimento próximo e tome ações rápidas de mitigação de desperdícios.
                </Typography>
            </Box>

            {alertas.length === 0 ? (
                <Card sx={{ borderRadius: 3, p: 5, textAlign: 'center', boxShadow: '0 4px 20px rgba(0,0,0,0.05)', bgcolor: 'success.50', border: '1px solid', borderColor: 'success.light' }}>
                    <TaskAlt color="success" sx={{ fontSize: 64, mb: 2 }} />
                    <Typography variant="h5" fontWeight="bold" gutterBottom color="success.dark">
                        Cozinha em Perfeito Controle!
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        Não há nenhum lote de ingrediente em estado de atenção de validade no momento.
                    </Typography>
                </Card>
            ) : (
                <Grid container spacing={3}>
                    {alertas.map(alerta => {
                        // Extrai as cores customizadas para o ícone e fundo do Avatar, garantindo contraste perfeito (sem vermelho-no-vermelho)
                        const { cor, label, Icon, bgIcone, corIcone } = definirSeveridade(alerta.mensagem);
                        return (
                            <Grid item xs={12} md={6} lg={4} key={alerta.id}>
                                <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', position: 'relative', overflow: 'visible', transition: 'transform 0.2s', '&:hover': { transform: 'translateY(-4px)' } }}>
                                    <Chip 
                                        label={label} 
                                        color={cor} 
                                        size="small" 
                                        sx={{ position: 'absolute', top: -12, right: 24, fontWeight: 'bold' }} 
                                    />
                                    <CardContent sx={{ pt: 4 }}>
                                        <Box sx={{ display: 'flex', alignItems: 'flex-start', mb: 2 }}>
                                            {/* Usamos o fundo translúcido bgIcone e cor viva corIcone definidos no helper de severidade */}
                                            <Avatar sx={{ bgcolor: bgIcone, color: corIcone, mr: 2 }}>
                                                <Icon />
                                            </Avatar>
                                            <Box>
                                                <Typography variant="h6" fontWeight="bold" lineHeight={1.2}>
                                                    Aviso de Validade
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary">
                                                    Gerado em: {new Date(alerta.dataAlerta).toLocaleDateString('pt-BR')}
                                                </Typography>
                                            </Box>
                                        </Box>
                                        <Typography variant="body2" color="text.secondary">
                                            {alerta.mensagem}
                                        </Typography>
                                    </CardContent>
                                    <CardActions sx={{ px: 2, pb: 2, pt: 0 }}>
                                        <Button 
                                            fullWidth 
                                            variant="outlined" 
                                            color="inherit" 
                                            startIcon={<DoneAll />} 
                                            onClick={() => marcarVisualizado(alerta.id)}
                                            sx={{ borderRadius: 2, color: 'text.secondary', borderColor: 'divider' }}
                                        >
                                            Marcar como Visto
                                        </Button>
                                    </CardActions>
                                </Card>
                            </Grid>
                        );
                    })}
                </Grid>
            )}
        </Box>
    );
}



