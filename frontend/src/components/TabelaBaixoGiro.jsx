import { 
    Card, CardContent, Typography, Box, Table, TableBody, TableCell, 
    TableContainer, TableHead, TableRow, Chip, Button, Avatar 
} from '@mui/material';
import TrendingDown from '@mui/icons-material/TrendingDown';
import WarningAmber from '@mui/icons-material/WarningAmber';
import OfflineBolt from '@mui/icons-material/OfflineBolt';
import AddCircle from '@mui/icons-material/AddCircle';
import { useNavigate } from 'react-router-dom';

// Componente que exibe a tabela de produtos com baixo giro (sem saída nos últimos 15 dias).
// O objetivo é alertar visualmente o gerente sobre produtos que precisam de ação antes de virarem desperdício.
export default function TabelaBaixoGiro({ dados, formatarMoeda }) {
    const navigate = useNavigate();

    return (
        <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: '100%', display: 'flex', flexDirection: 'column' }}>
            <CardContent sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', pb: 1 }}>

                {/* Cabeçalho do card com ícone de tendência de queda */}
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                    {/* Avatar com cor suave (error.light em opacidade baixa) para não competir com o vermelho da linha */}
                    <Avatar sx={{ bgcolor: 'rgba(244, 67, 54, 0.1)', color: 'error.main', mr: 2 }}>
                        <TrendingDown />
                    </Avatar>
                    <Box>
                        <Typography variant="h6" fontWeight="bold">Alerta de Baixo Giro</Typography>
                        <Typography variant="body2" color="text.secondary">
                            Itens do cardápio sem saída nos últimos 15 dias — risco de desperdício.
                        </Typography>
                    </Box>
                </Box>

                <TableContainer sx={{ flexGrow: 1, overflowY: 'auto', maxHeight: 330 }}>
                    <Table size="small" stickyHeader>
                        <TableHead>
                            <TableRow>
                                <TableCell sx={{ fontWeight: 'bold' }}>Cód.</TableCell>
                                <TableCell sx={{ fontWeight: 'bold' }}>Produto</TableCell>
                                <TableCell align="center" sx={{ fontWeight: 'bold' }}>Unid. Vendidas</TableCell>
                                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Receita Gerada</TableCell>
                                <TableCell align="center" sx={{ fontWeight: 'bold' }}>Ação Sugerida</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {dados?.map((item) => {
                                // Um produto é "crítico" se não vendeu nenhuma unidade
                                const isCritical = item.totalVendido === 0;

                                return (
                                    // LINHA CRÍTICA: em vez de fundo vermelho forte (que poluía visualmente),
                                    // usamos apenas um fundo ultra-sutil e uma borda lateral vermelha discreta.
                                    // Isso destaca a urgência sem criar o efeito "vermelho sobre vermelho".
                                    <TableRow
                                        key={item.id}
                                        hover
                                        sx={{
                                            bgcolor: isCritical ? 'rgba(244, 67, 54, 0.04)' : 'inherit',
                                            // Borda lateral esquerda funciona como "marcador de perigo" sem saturar a linha inteira
                                            borderLeft: isCritical ? '4px solid #f44336' : '4px solid transparent',
                                        }}
                                    >
                                        <TableCell sx={{ color: 'text.secondary' }}>#{item.id}</TableCell>
                                        <TableCell sx={{ fontWeight: 'bold' }}>{item.nome}</TableCell>
                                        <TableCell align="center">
                                            {/* Chip "outlined" para produtos críticos: mantém o destaque vermelho
                                                mas sem o fundo cheio que gerava a saturação visual */}
                                            <Chip 
                                                label={`${item.totalVendido} un.`} 
                                                size="small" 
                                                color={isCritical ? "error" : "default"} 
                                                variant={isCritical ? "outlined" : "outlined"}
                                                sx={{ fontWeight: 'bold' }}
                                            />
                                        </TableCell>
                                        <TableCell align="right" sx={{ fontWeight: 'bold' }}>
                                            {formatarMoeda(item.receitaTotal)}
                                        </TableCell>
                                        <TableCell align="center">
                                            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
                                                {/* Chip de sugestão de ação: Promoção Urgente ou Avaliar Campanha */}
                                                <Chip 
                                                    icon={isCritical ? <WarningAmber /> : <OfflineBolt />} 
                                                    label={isCritical ? "Promoção Urgente" : "Avaliar Campanha"} 
                                                    color={isCritical ? "error" : "warning"} 
                                                    variant="outlined"
                                                    size="small"
                                                />
                                                {/* Botão "Criar Promoção" aparece apenas para produtos com 0 vendas */}
                                                {isCritical && (
                                                    <Button 
                                                        variant="outlined"
                                                        color="error" 
                                                        size="small" 
                                                        startIcon={<AddCircle />}
                                                        onClick={() => navigate('/promocoes')}
                                                        sx={{ textTransform: 'none', borderRadius: 2 }}
                                                    >
                                                        Criar Promoção
                                                    </Button>
                                                )}
                                            </Box>
                                        </TableCell>
                                    </TableRow>
                                );
                            })}
                            
                            {/* Estado vazio: quando não há produtos com baixo giro */}
                            {(!dados || dados.length === 0) && (
                                <TableRow>
                                    <TableCell colSpan={5} align="center" sx={{ py: 4 }}>
                                        <Typography variant="body1" color="success.main" fontWeight="bold">
                                            Nenhum produto com baixo giro detectado. Desperdício mitigado!
                                        </Typography>
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



