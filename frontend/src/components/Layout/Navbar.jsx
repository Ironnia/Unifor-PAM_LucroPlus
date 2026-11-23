import { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { 
    AppBar, Toolbar, IconButton, Typography, Badge, Box, Avatar, Menu, MenuItem, Divider, ListItemIcon 
} from '@mui/material';
import { Menu as MenuIcon, Notifications, WarningAmber, LocalOffer } from '@mui/icons-material';

export default function Navbar({ title, drawerWidth, handleDrawerToggle }) {
    const { user } = useAuth();
    const [notificacoes, setNotificacoes] = useState([]);
    const [anchorEl, setAnchorEl] = useState(null);

    useEffect(() => {
        carregarAlertas();
    }, []);

    const carregarAlertas = async () => {
        try {
            const [alertasRes, promocoesRes] = await Promise.allSettled([
                api.get('/alertas/vencimento'),
                api.get('/promocoes/sugestoes')
            ]);
            
            const alertas = (alertasRes.status === 'fulfilled' && Array.isArray(alertasRes.value?.data))
                ? alertasRes.value.data.map(alerta => ({
                    id: `alerta-${alerta.id}`,
                    tipo: 'alerta',
                    mensagem: alerta.mensagem,
                    lido: alerta.visualizado
                }))
                : [];

            const promocoes = (promocoesRes.status === 'fulfilled' && Array.isArray(promocoesRes.value?.data))
                ? promocoesRes.value.data.map(promo => ({
                    id: `promo-${promo.id}`,
                    tipo: 'promocao',
                    mensagem: `Sugestão de ${promo.descontoPct}% OFF para ${promo.produto?.nome || 'Produto'}`,
                    lido: false
                }))
                : [];

            setNotificacoes([...alertas, ...promocoes]);
        } catch (error) {
            console.error('Erro ao carregar notificações', error);
        }
    };

    const naoLidas = notificacoes.filter(n => !n.lido).length;

    const handleMenuOpen = (event) => setAnchorEl(event.currentTarget);
    const handleMenuClose = () => setAnchorEl(null);

    return (
        <AppBar
            position="fixed"
            elevation={0}
            sx={{
                width: { md: `calc(100% - ${drawerWidth}px)` },
                ml: { md: `${drawerWidth}px` },
                bgcolor: 'background.paper',
                borderBottom: '1px solid rgba(0,0,0,0.05)',
                color: 'text.primary'
            }}
        >
            <Toolbar>
                <IconButton
                    color="inherit"
                    aria-label="open drawer"
                    edge="start"
                    onClick={handleDrawerToggle}
                    sx={{ mr: 2, display: { md: 'none' } }}
                >
                    <MenuIcon />
                </IconButton>
                <Typography variant="h6" noWrap component="div" fontWeight="bold" sx={{ flexGrow: 1 }}>
                    {title}
                </Typography>

                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <IconButton color="inherit" onClick={handleMenuOpen}>
                        <Badge badgeContent={naoLidas} color="error">
                            <Notifications />
                        </Badge>
                    </IconButton>

                    <Menu
                        anchorEl={anchorEl}
                        open={Boolean(anchorEl)}
                        onClose={handleMenuClose}
                        PaperProps={{
                            elevation: 3,
                            sx: { width: 320, mt: 1.5, borderRadius: 2 }
                        }}
                        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                    >
                        <Box sx={{ px: 2, py: 1.5 }}>
                            <Typography variant="subtitle1" fontWeight="bold">Notificações</Typography>
                            <Typography variant="body2" color="text.secondary">{naoLidas} novas</Typography>
                        </Box>
                        <Divider />
                        {notificacoes.length > 0 ? notificacoes.map((notificacao) => (
                            <MenuItem key={notificacao.id} onClick={handleMenuClose} sx={{ py: 1.5, gap: 1.5 }}>
                                <ListItemIcon sx={{ minWidth: 'auto', color: notificacao.tipo === 'alerta' ? 'warning.main' : 'success.main' }}>
                                    {notificacao.tipo === 'alerta' ? <WarningAmber /> : <LocalOffer />}
                                </ListItemIcon>
                                <Typography variant="body2" sx={{ whiteSpace: 'normal', fontWeight: notificacao.lido ? 400 : 600 }}>
                                    {notificacao.mensagem}
                                </Typography>
                            </MenuItem>
                        )) : (
                            <MenuItem disabled>
                                <Typography variant="body2">Nenhuma notificação</Typography>
                            </MenuItem>
                        )}
                        <Divider />
                        <MenuItem sx={{ justifyContent: 'center', color: 'primary.main' }}>
                            <Typography variant="body2" fontWeight="bold">Ver todas</Typography>
                        </MenuItem>
                    </Menu>

                    <Box sx={{ display: { xs: 'none', sm: 'flex' }, alignItems: 'center', gap: 1.5, borderLeft: '1px solid rgba(0,0,0,0.1)', pl: 2 }}>
                        <Avatar sx={{ bgcolor: 'primary.light', width: 36, height: 36, fontSize: '1rem', color: 'primary.dark', fontWeight: 'bold' }}>
                            {user?.nome?.charAt(0).toUpperCase()}
                        </Avatar>
                        <Box>
                            <Typography variant="body2" fontWeight="bold" lineHeight={1.2}>{user?.nome}</Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'capitalize' }}>{user?.tipo}</Typography>
                        </Box>
                    </Box>
                </Box>
            </Toolbar>
        </AppBar>
    );
}


