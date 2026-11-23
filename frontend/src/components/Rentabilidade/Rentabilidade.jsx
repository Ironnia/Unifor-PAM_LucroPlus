import { useEffect, useState } from 'react';
import api from '../../services/api';
import { 
    Card, CardContent, Typography, Box, Table, TableBody, TableCell, 
    TableContainer, TableHead, TableRow, CircularProgress, Chip, Grid 
} from '@mui/material';
import { Leaderboard } from '@mui/icons-material';

export default function Rentabilidade() {
    // Dados mockados curados para a tabela de rentabilidade por produto
    // Os valores simulam um cardápio real com margens variadas (Excelente / Atenção / Crítico)
    const [produtos, setProdutos] = useState([
        // Produtos com margem Excelente (verde, >= 50%) — estrelas do cardápio
        { produtoId: 1, produtoNome: "Petit Gâteau com Sorvete",  custoProducao:  8.50, precoVenda: 28.00, lucroBruto: 19.50, margemLucroPct: 69.64 },
        { produtoId: 2, produtoNome: "Risoto de Camarão",         custoProducao: 22.00, precoVenda: 68.00, lucroBruto: 46.00, margemLucroPct: 67.65 },
        { produtoId: 3, produtoNome: "Filé Mignon ao Molho",      custoProducao: 28.00, precoVenda: 79.00, lucroBruto: 51.00, margemLucroPct: 64.56 },
        // Produtos com margem de Atenção (laranja, 20–49%) — precisam de análise
        { produtoId: 4, produtoNome: "Fettuccine Alfredo",        custoProducao: 18.00, precoVenda: 42.00, lucroBruto: 24.00, margemLucroPct: 38.10 },
        { produtoId: 5, produtoNome: "Pizza Margherita",          custoProducao: 14.00, precoVenda: 45.00, lucroBruto: 31.00, margemLucroPct: 31.11 },
        { produtoId: 6, produtoNome: "Salmão Grelhado",           custoProducao: 32.00, precoVenda: 58.00, lucroBruto: 26.00, margemLucroPct: 44.83 },
        // Produtos com margem Crítica (vermelho, < 20%) — urgência para banca
        { produtoId: 7, produtoNome: "Mousse de Limão",           custoProducao: 13.50, precoVenda: 18.00, lucroBruto:  4.50, margemLucroPct: 15.00 },
        { produtoId: 8, produtoNome: "Suco de Uva Integral",      custoProducao: 10.50, precoVenda: 12.00, lucroBruto:  1.50, margemLucroPct:  8.33 },
    ]);

    const [loading, setLoading] = useState(true);

    // --- CARREGAMENTO DE DADOS REAIS DO BACKEND KTOR ---
    useEffect(() => {
        const carregarRentabilidade = async () => {
            try {
                setLoading(true);
                const response = await api.get('/produtos/rentabilidade');
                if (Array.isArray(response.data)) {
                    setProdutos(response.data);
                }
            } catch (error) {
                console.error("Erro ao carregar rentabilidade dos produtos:", error);
            } finally {
                setLoading(false);
            }
        };
        carregarRentabilidade();
    }, []);

    const formatarMoeda = (valor) => {
        return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor || 0);
    };

    const formatarPorcentagem = (valor) => {
        return `${(valor || 0).toFixed(2)}%`;
    };

    const getStatusProps = (margem) => {
        if (margem >= 50) return { color: 'success', label: 'Excelente' };
        if (margem >= 20) return { color: 'warning', label: 'Atenção' };
        return { color: 'error', label: 'Crítico' };
    };

    if (loading) {
        return (
            <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', mb: 3, height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 300 }}>
                <CircularProgress />
            </Card>
        );
    }

    const maisLucrativo = produtos.length > 0 ? produtos[0] : null;
    const menosLucrativo = produtos.length > 0 ? produtos[produtos.length - 1] : null;

    return (
        <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: '100%', display: 'flex', flexDirection: 'column' }}>
            <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', pb: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                    <Leaderboard color="primary" sx={{ mr: 1, fontSize: 32 }} />
                    <Box>
                        <Typography variant="h6" fontWeight="bold">Rentabilidade por Produto</Typography>
                        <Typography variant="caption" color="text.secondary">Margem baseada no custo real de produção.</Typography>
                    </Box>
                </Box>

                <Grid container spacing={2} sx={{ mb: 3 }}>
                    {maisLucrativo && (
                        <Grid item xs={12} sm={6}>
                            <Card variant="outlined" sx={{ borderRadius: 2, p: 2, borderColor: 'success.light', bgcolor: 'success.main' + '10' }}>
                                <Typography variant="caption" fontWeight="bold" color="success.main" sx={{ textTransform: 'uppercase' }}>Mais Lucrativo</Typography>
                                <Typography variant="body1" fontWeight="bold" sx={{ mt: 0.5 }}>{maisLucrativo.produtoNome}</Typography>
                                <Typography variant="h5" fontWeight="900" color="success.main">{formatarPorcentagem(maisLucrativo.margemLucroPct)}</Typography>
                                <Typography variant="caption" color="text.secondary">Lucro Bruto: {formatarMoeda(maisLucrativo.lucroBruto)}</Typography>
                            </Card>
                        </Grid>
                    )}
                    {menosLucrativo && (
                        <Grid item xs={12} sm={6}>
                            <Card variant="outlined" sx={{ borderRadius: 2, p: 2, borderColor: 'error.light', bgcolor: 'error.main' + '10' }}>
                                <Typography variant="caption" fontWeight="bold" color="error.main" sx={{ textTransform: 'uppercase' }}>Menor Rentabilidade</Typography>
                                <Typography variant="body1" fontWeight="bold" sx={{ mt: 0.5 }}>{menosLucrativo.produtoNome}</Typography>
                                <Typography variant="h5" fontWeight="900" color="error.main">{formatarPorcentagem(menosLucrativo.margemLucroPct)}</Typography>
                                <Typography variant="caption" color="text.secondary">Lucro Bruto: {formatarMoeda(menosLucrativo.lucroBruto)}</Typography>
                            </Card>
                        </Grid>
                    )}
                </Grid>

                <TableContainer sx={{ flexGrow: 1, maxHeight: 215, overflowY: 'auto' }}>
                    <Table size="small" stickyHeader>
                        <TableHead>
                            <TableRow>
                                <TableCell sx={{ fontWeight: 'bold' }}>Produto</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Custo (R$)</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Preço (R$)</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Lucro (R$)</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Margem (%)</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {produtos.map((produto) => {
                                const status = getStatusProps(produto.margemLucroPct);
                                return (
                                    <TableRow key={produto.produtoId} hover>
                                        <TableCell sx={{ fontWeight: 'bold' }}>{produto.produtoNome}</TableCell>
                                        <TableCell align="right">{formatarMoeda(produto.custoProducao)}</TableCell>
                                        <TableCell align="right">{formatarMoeda(produto.precoVenda)}</TableCell>
                                        <TableCell align="right" sx={{ color: produto.lucroBruto < 0 ? 'error.main' : 'success.main', fontWeight: 'bold' }}>
                                            {formatarMoeda(produto.lucroBruto)}
                                        </TableCell>
                                        <TableCell align="right">
                                            <Chip label={formatarPorcentagem(produto.margemLucroPct)} color={status.color} size="small" sx={{ fontWeight: 'bold', minWidth: 60 }} />
                                        </TableCell>
                                    </TableRow>
                                );
                            })}
                            {produtos.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={5} align="center">
                                        Nenhum dado de rentabilidade encontrado.
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>
            </CardContent>
        </Card>
    );
}



