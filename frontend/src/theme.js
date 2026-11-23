import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    primary: {
      main: '#004AF7', // Anil
      dark: '#132190', // Marinho
      light: '#87B7FE', // Azul Claro
    },
    background: {
      default: '#F5F7FA', // Fundo principal B2B
      paper: '#FFFFFF', // Fundo de cards/painéis
    },
    success: {
      main: '#4CAF50',
    },
    warning: {
      main: '#FF9800',
    },
    error: {
      main: '#F44336',
    },
    uniforGelo: {
      main: '#E4F2FE', // Fundo Gelo para painéis de destaque
    },
    text: {
      primary: '#333333',
      secondary: '#666666',
    }
  },
  typography: {
    fontFamily: '"Satoshi", "Roboto", "Helvetica", "Arial", sans-serif',
    h1: { fontWeight: 700 },
    h2: { fontWeight: 700 },
    h3: { fontWeight: 700 },
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 700 },
    button: { fontWeight: 500, textTransform: 'none' }, // Remove caps lock padrão do MUI
  },
  shape: {
    borderRadius: 12, // Borda padrão Android/DPM
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          boxShadow: 'none',
          '&:hover': {
            boxShadow: 'none',
          },
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          boxShadow: '0px 2px 4px rgba(0, 0, 0, 0.05)',
        }
      }
    }
  },
});



