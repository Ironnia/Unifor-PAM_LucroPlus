import { useEffect, useState, useRef } from 'react';
import api from '../../services/api';
import { 
    Box, Typography, Grid, Card, CardContent, TextField, Button,
    Paper, CircularProgress, Chip, useTheme, Skeleton
} from '@mui/material';
import { Settings, Save, Hub, Terminal, Storage } from '@mui/icons-material';

export default function Configuracoes() {
    // Estados do formulário de conexão JDBC
    const [url, setUrl] = useState('');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');

    // Estados de UI e Feedback
    const [loading, setLoading] = useState(true);
    const [salvando, setSalvando] = useState(false);
    const [statusConexao, setStatusConexao] = useState('idle'); // idle | testing | success | failed
    const [consoleOutput, setConsoleOutput] = useState([
        'LOG: Painel carregado. Pronto para receber credenciais do PDV fictício.',
        'LOG: Por padrão, o sistema usará o banco de dados local "pdv_ficticio".'
    ]);

    const terminalEndRef = useRef(null);
    const theme = useTheme();

    useEffect(() => {
        carregarConfiguracoes();
    }, []);

    useEffect(() => {
        terminalEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [consoleOutput]);

    const carregarConfiguracoes = async () => {
        try {
            setLoading(true);
            const response = await api.get('/configuracoes/pdv');
            const data = response.data;
            if (data) {
                setUrl(data.url || '');
                setUsername(data.username || '');
                setPassword(data.password || '');
                registrarLog('LOG: Parâmetros JDBC carregados do banco LucroPlus com sucesso.');
            }
        } catch (error) {
            console.error('Erro ao buscar configurações:', error);
            registrarLog('ERROR: Falha de sincronização. O banco de dados do LucroPlus está inalcançável.');
        } finally {
            setLoading(false);
        }
    };

    const registrarLog = (mensagem) => {
        const hora = new Date().toLocaleTimeString('pt-BR');
        setConsoleOutput(prev => [...prev, `[${hora}] ${mensagem}`]);
    };

    const handleSalvar = async (e) => {
        e.preventDefault();
        setSalvando(true);
        registrarLog('LOG: Iniciando persistência das novas credenciais JDBC...');
        try {
            await api.post('/configuracoes/pdv', { url, username, password });
            registrarLog('SUCCESS: Credenciais gravadas na tabela tb_configuracao perfeitamente!');
        } catch (error) {
            console.error('Erro ao salvar credenciais:', error);
            registrarLog('ERROR: Negativa do banco de dados na gravação dos parâmetros.');
        } finally {
            setSalvando(false);
        }
    };

    const handleTestarConexao = async () => {
        setStatusConexao('testing');
        registrarLog('LOG: Despachando ping JDBC ao banco do PDV fictício...');
        try {
            const response = await api.post('/configuracoes/pdv/testar-conexao', { url, username, password });
            if (response.data?.sucesso) {
                setStatusConexao('success');
                registrarLog('SUCCESS: Conexão JDBC validada' +
                    '! Base externa respondendo com perfeição.');
            } else {
                setStatusConexao('failed');
                registrarLog(`ERROR: Conexão recusada: ${response.data?.mensagem || 'Sem resposta do host'}`);
            }
        } catch (error) {
            setStatusConexao('failed');
            console.error('Erro ao testar conexão:', error);
            registrarLog('ERROR: Timeout ou erro DNS ao tentar resolver host de banco de dados.');
        }
    };

    const formatarLinhaConsole = (linha) => {
        if (linha.includes('SUCCESS:')) {
            const partes = linha.split('SUCCESS:');
            return (
                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#4caf50' }}>
                    <span style={{ color: '#888', marginRight: '8px' }}>{partes[0]}</span>
                    <strong>SUCCESS:</strong>{partes[1]}
                </Typography>
            );
        }
        if (linha.includes('ERROR:')) {
            const partes = linha.split('ERROR:');
            return (
                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#f44336' }}>
                    <span style={{ color: '#888', marginRight: '8px' }}>{partes[0]}</span>
                    <strong>ERROR:</strong>{partes[1]}
                </Typography>
            );
        }
        if (linha.includes('LOG:')) {
            const partes = linha.split('LOG:');
            return (
                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#2196f3' }}>
                    <span style={{ color: '#888', marginRight: '8px' }}>{partes[0]}</span>
                    <strong>LOG:</strong>{partes[1]}
                </Typography>
            );
        }
        return <Typography variant="body2" sx={{ fontFamily: 'monospace', color: '#e0e0e0' }}>{linha}</Typography>;
    };

    if (loading) {
        return (
            <Box sx={{ pb: 4 }}>
                <Box sx={{ mb: 4 }}>
                    <Skeleton width={300} height={40} />
                    <Skeleton width={500} height={20} />
                </Box>
                <Grid container spacing={3}>
                    <Grid item xs={12} md={6}>
                        <Skeleton variant="rectangular" height={380} sx={{ borderRadius: 3 }} />
                    </Grid>
                    <Grid item xs={12} md={6}>
                        <Skeleton variant="rectangular" height={380} sx={{ borderRadius: 3 }} />
                    </Grid>
                </Grid>
            </Box>
        );
    }

    const getStatusChipProps = () => {
        switch(statusConexao) {
            case 'testing': return { label: 'Pingando JDBC...', color: 'warning', variant: 'outlined' };
            case 'success': return { label: 'Serviço Online', color: 'success', variant: 'filled' };
            case 'failed': return { label: 'Sem Conexão', color: 'error', variant: 'filled' };
            default: return { label: 'Não Testado', color: 'default', variant: 'outlined' };
        }
    };

    return (
        <Box sx={{ pb: 4 }}>
            <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
                <Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
                        <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'secondary.light', display: 'flex' }}>
                            <Settings sx={{ color: 'secondary.main', fontSize: 28 }} />
                        </Box>
                        <Typography variant="h4" fontWeight="bold" color="text.primary">
                            Configurações do PDV
                        </Typography>
                    </Box>
                    <Typography variant="body1" color="text.secondary">
                        Conecte o Motor do LucroPlus ao banco externo do seu PDV (Ponto de Venda) fictício.
                    </Typography>
                </Box>
                <Chip {...getStatusChipProps()} sx={{ fontWeight: 'bold', px: 1 }} />
            </Box>

            <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
                    <Card sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', height: '100%' }}>
                        <CardContent sx={{ p: 4 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                                <Storage sx={{ mr: 1, color: 'primary.main' }} />
                                <Typography variant="h6" fontWeight="bold">Parâmetros de Integração JDBC</Typography>
                            </Box>
                            
                            <form onSubmit={handleSalvar}>
                                <TextField
                                    fullWidth
                                    label="JDBC URL de Conexão"
                                    value={url}
                                    onChange={(e) => setUrl(e.target.value)}
                                    placeholder="jdbc:mysql://localhost:3306/pdv_ficticio"
                                    required
                                    sx={{ mb: 3 }}
                                    helperText="String de comunicação oficial para conexões JDBC MySQL."
                                />
                                <Grid container spacing={2} sx={{ mb: 4 }}>
                                    <Grid item xs={12} sm={6}>
                                        <TextField
                                            fullWidth
                                            label="Usuário da Base"
                                            value={username}
                                            onChange={(e) => setUsername(e.target.value)}
                                            placeholder="root"
                                            required
                                        />
                                    </Grid>
                                    <Grid item xs={12} sm={6}>
                                        <TextField
                                            fullWidth
                                            label="Senha da Base"
                                            type="password"
                                            value={password}
                                            onChange={(e) => setPassword(e.target.value)}
                                            placeholder="••••••••"
                                        />
                                    </Grid>
                                </Grid>
                                
                                <Box sx={{ display: 'flex', gap: 2 }}>
                                    <Button
                                        fullWidth
                                        variant="outlined"
                                        color="inherit"
                                        onClick={handleTestarConexao}
                                        disabled={statusConexao === 'testing' || salvando}
                                        startIcon={statusConexao === 'testing' ? <CircularProgress size={20} /> : <Hub />}
                                        sx={{ borderRadius: 2 }}
                                    >
                                        Testar Conexão
                                    </Button>
                                    <Button
                                        fullWidth
                                        type="submit"
                                        variant="contained"
                                        disabled={salvando || statusConexao === 'testing'}
                                        startIcon={salvando ? <CircularProgress size={20} color="inherit" /> : <Save />}
                                        sx={{ borderRadius: 2 }}
                                    >
                                        {salvando ? 'Gravando...' : 'Salvar'}
                                    </Button>
                                </Box>
                            </form>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} md={6}>
                    <Paper 
                        sx={{ 
                            borderRadius: 3, 
                            bgcolor: '#121212', 
                            color: '#fff', 
                            height: '100%', 
                            display: 'flex', 
                            flexDirection: 'column',
                            overflow: 'hidden',
                            boxShadow: '0 8px 32px rgba(0,0,0,0.2)'
                        }}
                    >
                        <Box sx={{ bgcolor: '#1e1e1e', px: 2, py: 1.5, display: 'flex', alignItems: 'center', borderBottom: '1px solid #333' }}>
                            <Box sx={{ display: 'flex', gap: 1, mr: 2 }}>
                                <Box sx={{ borderRadius: '50%', bgcolor: '#ff5f56', width: 12, height: 12 }} />
                                <Box sx={{ borderRadius: '50%', bgcolor: '#ffbd2e', width: 12, height: 12 }} />
                                <Box sx={{ borderRadius: '50%', bgcolor: '#27c93f', width: 12, height: 12 }} />
                            </Box>
                            <Terminal sx={{ color: '#888', fontSize: 16, mr: 1 }} />
                            <Typography variant="caption" sx={{ color: '#888', fontFamily: 'monospace' }}>
                            lucroplus-etl-service ~ bash
                            </Typography>
                        </Box>
                        <Box sx={{ p: 2, flexGrow: 1, overflowY: 'auto', maxHeight: 380, display: 'flex', flexDirection: 'column' }}>
                            {consoleOutput.map((logStr, i) => (
                                <Box key={i} sx={{ mb: 0.5 }}>
                                    {formatarLinhaConsole(logStr)}
                                </Box>
                            ))}
                            <div ref={terminalEndRef} />
                        </Box>
                    </Paper>
                </Grid>
            </Grid>
        </Box>
    );
}



