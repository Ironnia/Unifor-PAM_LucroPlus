import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { 
    Box, 
    Button, 
    TextField, 
    Typography, 
    Paper, 
    InputAdornment, 
    CircularProgress,
    Alert
} from '@mui/material';
import { MailOutlined, LockOutlined, InfoOutlined, RestaurantMenu } from '@mui/icons-material';

export default function Login() {
    const [email, setEmail] = useState('');
    const [senha, setSenha] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const { signIn } = useAuth();
    const navigate = useNavigate();

    async function handleSubmit(e) {
        e.preventDefault();
        setError('');
        setLoading(true);

        if (!email || !senha) {
            setError('Preencha todos os campos obrigatórios.');
            setLoading(false);
            return;
        }

        const result = await signIn({ email, senha });

        if (result.success) {
            navigate('/dashboard');
        } else {
            setError(typeof result.message === 'string' ? result.message : 'Credenciais inválidas. Tente novamente.');
        }

        setLoading(false);
    }

    return (
        <Box sx={{ display: 'flex', minHeight: '100vh', backgroundColor: '#F5F7FA' }}>
            {/* Lado Esquerdo - Branding (Anil/Marinho) */}
            <Box 
                sx={{ 
                    flex: 1, 
                    display: { xs: 'none', md: 'flex' },
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center',
                    background: 'linear-gradient(135deg, #004AF7 0%, #132190 100%)', // Anil para Marinho
                    color: 'white',
                    padding: 4
                }}
            >
                {/* Ícone Genérico do LucroPlus (Placeholder) */}
                <Box sx={{ 
                    width: 100, height: 100, 
                    backgroundColor: 'rgba(255,255,255,0.15)', 
                    borderRadius: '50%', 
                    display: 'flex', justifyContent: 'center', alignItems: 'center',
                    mb: 3
                }}>
                    <RestaurantMenu sx={{ fontSize: 50, color: 'white' }} />
                </Box>

                <Typography variant="h3" fontWeight="700" sx={{ mb: 1 }}>
                    LucroPlus
                </Typography>
                <Typography variant="subtitle1" sx={{ opacity: 0.8, mb: 10, textAlign: 'center', maxWidth: 300 }}>
                    Sistema Inteligente de Gestão de Validades e Promoções
                </Typography>

                {/* Selo de Credibilidade Unifor */}
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: 0.6 }}>
                    <Box 
                        component="img" 
                        src="/logo-unifor.png" 
                        alt="Logo Unifor" 
                        sx={{ width: '120px' }} 
                    />
                </Box>
            </Box>

            {/* Lado Direito - Formulário de Login */}
            <Box 
                sx={{ 
                    flex: 1, 
                    display: 'flex', 
                    flexDirection: 'column',
                    justifyContent: 'center', 
                    alignItems: 'center',
                    padding: 4
                }}
            >
                <Paper 
                    elevation={0}
                    sx={{ 
                        width: '100%', 
                        maxWidth: 400, 
                        p: 5, 
                        borderRadius: 3,
                        boxShadow: '0 4px 24px rgba(0,0,0,0.05)'
                    }}
                >
                    {/* Exibe a logo na versão mobile apenas */}
                    <Box 
                        component="img" 
                        src="/logo-unifor.png" 
                        alt="Logo Unifor" 
                        sx={{ 
                            width: '150px', 
                            display: { xs: 'block', md: 'none' }, 
                            margin: '0 auto 24px auto',
                            filter: 'invert(1)' // Gambiarra provisória caso a tela fique branca e a logo seja branca
                        }} 
                    />

                    <Typography variant="h5" fontWeight="700" color="text.primary" sx={{ mb: 1 }}>
                        Acesso ao Sistema
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
                        Insira suas credenciais corporativas para continuar.
                    </Typography>

                    <form onSubmit={handleSubmit} noValidate>
                        <TextField
                            fullWidth
                            id="email"
                            label="E-mail"
                            variant="outlined"
                            margin="normal"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            disabled={loading}
                            InputProps={{
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <MailOutlined color="action" />
                                    </InputAdornment>
                                ),
                            }}
                            sx={{ mb: 2 }}
                        />

                        <TextField
                            fullWidth
                            id="senha"
                            label="Senha"
                            type="password"
                            variant="outlined"
                            margin="normal"
                            value={senha}
                            onChange={(e) => setSenha(e.target.value)}
                            disabled={loading}
                            InputProps={{
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <LockOutlined color="action" />
                                    </InputAdornment>
                                ),
                            }}
                            sx={{ mb: 3 }}
                        />

                        {error && (
                            <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>
                                {error}
                            </Alert>
                        )}

                        <Button
                            type="submit"
                            fullWidth
                            variant="contained"
                            color="primary"
                            size="large"
                            disabled={loading}
                            sx={{ py: 1.5, mb: 4, fontWeight: 'bold' }}
                        >
                            {loading ? <CircularProgress size={24} color="inherit" /> : 'Entrar no Painel'}
                        </Button>

                    </form>
                </Paper>
            </Box>
        </Box>
    );
}


