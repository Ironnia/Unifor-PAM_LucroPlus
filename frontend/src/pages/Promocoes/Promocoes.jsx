import { useEffect, useState } from 'react';
import api from '../../services/api';
import { 
    Box, Typography, Grid, Card, CardContent, CardActions, 
    Button, Chip, Skeleton, useTheme, Avatar, Divider 
} from '@mui/material';
import { 
    AutoAwesome, Celebration, Percent, Lightbulb, Check, Close 
} from '@mui/icons-material';

export default function Promocoes() {
    const theme = useTheme();
    const [promocoes, setPromocoes] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        carregarPromocoes();
    }, []);

    const carregarPromocoes = async () => {
        try {
            setLoading(true);
            const response = await api.get('/promocoes/sugestoes');
            if (Array.isArray(response.data)) {
                setPromocoes(response.data);
            }
        } catch (error) {
            console.error('Erro ao buscar sugestões:', error);
        } finally {
            setLoading(false);
        }
    };

    const ativarPromocao = async (id) => {
        try {
            await api.patch(`/promocoes/${id}/ativar`);
            setPromocoes(prev => prev.filter(p => p.id !== id));
        } catch (error) {
            console.error('Erro ao ativar promoção:', error);
        }
    };

    const recusarPromocao = async (id) => {
        try {
            await api.patch(`/promocoes/${id}/recusar`);
            setPromocoes(prev => prev.filter(p => p.id !== id));
        } catch (error) {
            console.error('Erro ao recusar promoção:', error);
        }
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
                                    <Skeleton variant="rectangular" height={100} sx={{ borderRadius: 2, mb: 2 }} />
                                    <Skeleton width="60%" height={24} sx={{ mb: 1 }} />
                                    <Skeleton width="100%" height={20} />
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
                    {/* Fundo laranja/amarelo translúcido suave com o ícone warning para um visual premium e de alto contraste */}
                    <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'rgba(255, 152, 0, 0.12)', display: 'flex' }}>
                        <AutoAwesome sx={{ color: 'warning.main', fontSize: 28 }} />
                    </Box>
                    <Typography variant="h4" fontWeight="bold" color="text.primary">
                        Motor de Promoções
                    </Typography>
                </Box>
                <Typography variant="body1" color="text.secondary">
                    Veja as sugestões automáticas baseadas em inteligência de validade e evite o prejuízo.
                </Typography>
            </Box>

            {promocoes.length === 0 ? (
                <Card sx={{ borderRadius: 3, p: 5, textAlign: 'center', boxShadow: '0 4px 20px rgba(0,0,0,0.05)', bgcolor: 'warning.50', border: '1px solid', borderColor: 'warning.light' }}>
                    <Celebration color="warning" sx={{ fontSize: 64, mb: 2 }} />
                    <Typography variant="h5" fontWeight="bold" gutterBottom color="warning.dark">
                        Tudo Atualizado!
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        O Motor LucroPlus não identificou nenhuma nova oportunidade urgente de promoção no momento.
                    </Typography>
                </Card>
            ) : (
                <Grid container spacing={3}>
                    {promocoes.map(promo => {
                        const isSuperDesconto = promo.descontoPct >= 25;
                        const precoOriginal = promo.produto.preco;
                        const precoComDesconto = precoOriginal * (1 - promo.descontoPct / 100);

                        return (
                            <Grid item xs={12} md={6} lg={4} key={promo.id}>
                                <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', position: 'relative', overflow: 'visible', transition: 'transform 0.2s', '&:hover': { transform: 'translateY(-4px)' } }}>
                                    <Chip 
                                        icon={<Percent />} 
                                        label={`${promo.descontoPct}% OFF`} 
                                        color={isSuperDesconto ? "error" : "primary"} 
                                        sx={{ position: 'absolute', top: -12, right: 24, fontWeight: '900', px: 1, boxShadow: 2 }} 
                                    />
                                    
                                    <CardContent sx={{ pt: 4 }}>
                                        <Typography variant="h5" fontWeight="bold" gutterBottom>
                                            {promo.produto.nome}
                                        </Typography>
                                        
                                        <Box sx={{ display: 'flex', alignItems: 'center', bgcolor: 'background.default', p: 1.5, borderRadius: 2, mb: 3 }}>
                                            <Lightbulb color="warning" sx={{ mr: 1, fontSize: 20 }} />
                                            <Typography variant="body2" color="text.secondary">
                                                <strong>Recomendação:</strong> {promo.motivo}
                                            </Typography>
                                        </Box>
                                        
                                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                            <Box>
                                                <Typography variant="caption" color="text.disabled" sx={{ textDecoration: 'line-through' }}>
                                                    R$ {precoOriginal.toFixed(2)}
                                                </Typography>
                                                <Typography variant="h5" fontWeight="900" color="success.main">
                                                    R$ {precoComDesconto.toFixed(2)}
                                                </Typography>
                                            </Box>
                                        </Box>
                                    </CardContent>
                                    
                                    <Divider />
                                    
                                    <CardActions sx={{ p: 2, justifyContent: 'space-between' }}>
                                        <Button 
                                            variant="outlined" 
                                            color="inherit" 
                                            startIcon={<Close />} 
                                            onClick={() => recusarPromocao(promo.id)}
                                            sx={{ borderRadius: 2, color: 'text.secondary', borderColor: 'divider', width: '48%' }}
                                        >
                                            Recusar
                                        </Button>
                                        <Button 
                                            variant="contained" 
                                            color="primary" 
                                            startIcon={<Check />} 
                                            onClick={() => ativarPromocao(promo.id)}
                                            sx={{ borderRadius: 2, width: '48%' }}
                                        >
                                            Ativar
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



