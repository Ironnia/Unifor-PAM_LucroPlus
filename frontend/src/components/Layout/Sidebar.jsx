import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { 
    Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, 
    Typography, Badge, Divider, Button, Avatar 
} from '@mui/material';
import {
    Dashboard, UploadFile, NotificationsActive, Group, Settings, 
    LocalOffer, InsertChart, Logout, RestaurantMenu
} from '@mui/icons-material';

export default function Sidebar({ drawerWidth, mobileOpen, handleDrawerToggle, isMobile }) {
    const { user, signOut } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const handleLogout = () => {
        signOut();
        navigate('/login');
    };

    const menuComum = [
        { path: '/dashboard', label: 'Painel de Validade', icon: <Dashboard /> },
        { path: '/importacao', label: 'Importação de Dados', icon: <UploadFile /> },
        { path: '/alertas', label: 'Central de Alertas', icon: <NotificationsActive />, badge: 3 },
    ];

    const menuAdmin = [
        { path: '/usuarios', label: 'Usuários', icon: <Group /> },
        { path: '/configuracoes', label: 'Configuração do PDV', icon: <Settings /> },
    ];

    const menuGerente = [
        { path: '/promocoes', label: 'Promoções Recomendadas', icon: <LocalOffer />, badge: 2 },
        { path: '/relatorios', label: 'Histórico de Desperdício', icon: <InsertChart /> },
    ];

    const menuPerfil = user?.tipo === 'ADMIN' ? menuAdmin : menuGerente;
    const todosMenus = [...menuComum, ...menuPerfil];

    const drawerContent = (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'primary.dark', color: 'white' }}>
            <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', mt: 2 }}>
                {/* Ícone Genérico do LucroPlus (Placeholder) */}
                <Box sx={{ 
                    width: 60, height: 60, 
                    backgroundColor: 'rgba(255,255,255,0.15)', 
                    borderRadius: '50%', 
                    display: 'flex', justifyContent: 'center', alignItems: 'center',
                    mb: 1.5
                }}>
                    <RestaurantMenu sx={{ fontSize: 32, color: 'white' }} />
                </Box>
                <Typography variant="h5" fontWeight="bold" sx={{ letterSpacing: 1 }}>LucroPlus</Typography>
                <Typography variant="caption" sx={{ opacity: 0.7, letterSpacing: 2 }}>PAINEL GERENCIAL</Typography>
            </Box>

            <Divider sx={{ borderColor: 'rgba(255,255,255,0.1)' }} />

            <List sx={{ flexGrow: 1, px: 2, py: 2 }}>
                {todosMenus.map((item) => {
                    const isActive = location.pathname.startsWith(item.path);
                    return (
                        <ListItem key={item.path} disablePadding sx={{ mb: 1 }}>
                            <ListItemButton
                                component={NavLink}
                                to={item.path}
                                onClick={isMobile ? handleDrawerToggle : undefined}
                                sx={{
                                    borderRadius: 2,
                                    bgcolor: isActive ? 'rgba(255,255,255,0.15)' : 'transparent',
                                    '&:hover': { bgcolor: 'rgba(255,255,255,0.1)' }
                                }}
                            >
                                <ListItemIcon sx={{ color: 'white', minWidth: 40 }}>
                                    {item.badge ? (
                                        <Badge badgeContent={item.badge} color="error" variant="dot">
                                            {item.icon}
                                        </Badge>
                                    ) : (
                                        item.icon
                                    )}
                                </ListItemIcon>
                                <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: '0.9rem', fontWeight: isActive ? 600 : 400 }} />
                            </ListItemButton>
                        </ListItem>
                    );
                })}
            </List>

            <Divider sx={{ borderColor: 'rgba(255,255,255,0.1)' }} />

            <Box sx={{ p: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, px: 1 }}>
                    <Avatar sx={{ bgcolor: 'primary.main', width: 32, height: 32, fontSize: '1rem' }}>
                        {user?.nome?.charAt(0).toUpperCase()}
                    </Avatar>
                    <Box>
                        <Typography variant="body2" fontWeight="bold" noWrap sx={{ maxWidth: 120 }}>{user?.nome}</Typography>
                        <Typography variant="caption" sx={{ opacity: 0.7 }}>{user?.tipo}</Typography>
                    </Box>
                </Box>
                <Button 
                    fullWidth 
                    variant="outlined" 
                    color="inherit" 
                    startIcon={<Logout />} 
                    onClick={handleLogout}
                    sx={{ borderColor: 'rgba(255,255,255,0.3)', '&:hover': { borderColor: 'white' }, mb: 1.5 }}
                >
                    Sair do Sistema
                </Button>

                {/* Selo Unifor no rodapé da Sidebar */}
                <Box sx={{ display: 'flex', justifyContent: 'center', opacity: 0.4 }}>
                    <Box 
                        component="img" 
                        src="/logo-unifor.png" 
                        alt="Logo Unifor" 
                        sx={{ width: '130px' }} 
                    />
                </Box>
            </Box>
        </Box>
    );

    return (
        <Box component="nav" sx={{ width: { md: drawerWidth }, flexShrink: { md: 0 } }}>
            <Drawer
                variant={isMobile ? "temporary" : "permanent"}
                open={isMobile ? mobileOpen : true}
                onClose={handleDrawerToggle}
                ModalProps={{ keepMounted: true }}
                sx={{
                    '& .MuiDrawer-paper': { 
                        boxSizing: 'border-box', 
                        width: drawerWidth, 
                        borderRight: 'none',
                        bgcolor: 'primary.dark'
                    },
                }}
            >
                {drawerContent}
            </Drawer>
        </Box>
    );
}


