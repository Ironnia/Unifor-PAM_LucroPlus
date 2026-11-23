import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Box, CssBaseline, useMediaQuery, useTheme } from '@mui/material';
import Sidebar from './Sidebar';
import Navbar from './Navbar';

const drawerWidth = 260;

export default function Layout({ title = 'Dashboard' }) {
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));
    const [mobileOpen, setMobileOpen] = useState(false);

    const handleDrawerToggle = () => {
        setMobileOpen(!mobileOpen);
    };

    return (
        <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default', width: '100%' }}>
            <CssBaseline />
            <Navbar 
                title={title} 
                drawerWidth={drawerWidth} 
                handleDrawerToggle={handleDrawerToggle} 
            />
            
            <Sidebar 
                drawerWidth={drawerWidth} 
                mobileOpen={mobileOpen} 
                handleDrawerToggle={handleDrawerToggle} 
                isMobile={isMobile}
            />

            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    p: { xs: 2, md: 4 },
                    width: { md: `calc(100% - ${drawerWidth}px)` },
                    mt: 8 // offset for the AppBar
                }}
            >
                <Outlet />
            </Box>
        </Box>
    );
}


