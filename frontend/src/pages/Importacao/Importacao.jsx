import React, { useState, useRef } from 'react';
import api from '../../services/api';
import { 
    Box, Typography, Tabs, Tab, Paper, Button, CircularProgress, 
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
    Chip, Alert, Grid, useTheme, Card, CardContent
} from '@mui/material';
import { 
    CloudUpload, Description, CheckCircle, Error, 
    Sync, LightbulbOutlined, UploadFile
} from '@mui/icons-material';

const TABS = [
    { 
        id: 'produtos', title: 'Produtos', endpoint: '/importacao/produtos', 
        templateName: 'produtos.csv', columns: ['nome', 'descricao', 'preco', 'categoria'] 
    },
    { 
        id: 'ingredientes', title: 'Ingredientes', endpoint: '/importacao/ingredientes',
        templateName: 'ingredientes.csv', columns: ['nome_ingrediente', 'unidade', 'quantidade', 'custo_unitario', 'data_validade', 'numero_lote', 'observacao']
    },
    { 
        id: 'ficha-tecnica', title: 'Ficha Técnica', endpoint: '/importacao/ficha-tecnica',
        templateName: 'ficha_tecnica.csv', columns: ['nome_produto', 'nome_ingrediente', 'quantidade', 'unidade']
    },
    { 
        id: 'vendas', title: 'Vendas', endpoint: '/importacao/vendas',
        templateName: 'vendas.csv', columns: ['nome_produto', 'data_venda', 'quantidade']
    },
    {
        id: 'pdv', title: 'Conectar PDV', endpoint: '/importacao/pdv',
        isPdv: true
    }
];

export default function Importacao() {
    const theme = useTheme();
    const [tabIndex, setTabIndex] = useState(0);
    const activeTab = TABS[tabIndex];
    
    const [file, setFile] = useState(null);
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null);
    const [isDragging, setIsDragging] = useState(false);
    const fileInputRef = useRef(null);

    const handleTabChange = (event, newValue) => {
        setTabIndex(newValue);
        setFile(null);
        setResult(null);
        setIsDragging(false);
    };

    const handleDragEnter = (e) => {
        e.preventDefault(); e.stopPropagation(); setIsDragging(true);
    };
    const handleDragLeave = (e) => {
        e.preventDefault(); e.stopPropagation(); setIsDragging(false);
    };
    const handleDragOver = (e) => {
        e.preventDefault(); e.stopPropagation(); setIsDragging(true);
    };
    const handleDrop = (e) => {
        e.preventDefault(); e.stopPropagation(); setIsDragging(false);
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            validateAndSetFile(e.dataTransfer.files[0]);
        }
    };
    const handleFileChange = (e) => {
        if (e.target.files && e.target.files.length > 0) {
            validateAndSetFile(e.target.files[0]);
        }
    };
    const validateAndSetFile = (selectedFile) => {
        if (selectedFile.name.endsWith('.csv')) {
            setFile(selectedFile);
            setResult(null);
        } else {
            alert('Por favor, selecione um arquivo válido (.csv)');
        }
    };

    const handleUpload = async () => {
        if (!file) return;
        setLoading(true); setResult(null);
        const formData = new FormData();
        formData.append('arquivo', file);

        try {
            const response = await api.post(activeTab.endpoint, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
            setResult(response.data);
        } catch (error) {
            setResult(error.response?.data || { sucesso: false, mensagem: 'Erro de comunicação', erros: [] });
        } finally {
            setLoading(false);
        }
    };

    const handlePdvSync = async () => {
        setLoading(true); setResult(null);
        try {
            const response = await api.post(activeTab.endpoint);
            setResult(response.data);
        } catch (error) {
            setResult(error.response?.data || { sucesso: false, mensagem: 'Erro de comunicação', erros: [] });
        } finally {
            setLoading(false);
        }
    };

    return (
        <Box sx={{ pb: 4 }}>
            <Box sx={{ mb: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
                    <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'primary.light', display: 'flex' }}>
                        <UploadFile sx={{ color: 'primary.main', fontSize: 28 }} />
                    </Box>
                    <Typography variant="h4" fontWeight="bold" color="text.primary">
                        Importação de Dados
                    </Typography>
                </Box>
                <Typography variant="body1" color="text.secondary">
                    Abasteça o sistema enviando planilhas CSV ou conectando ao PDV.
                </Typography>
            </Box>

            <Paper sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(0,0,0,0.05)', overflow: 'hidden', mb: 4 }}>
                <Tabs 
                    value={tabIndex} 
                    onChange={handleTabChange} 
                    variant="scrollable"
                    scrollButtons="auto"
                    sx={{ borderBottom: 1, borderColor: 'divider', px: 2, pt: 1 }}
                >
                    {TABS.map((tab, idx) => (
                        <Tab key={tab.id} label={tab.title} sx={{ fontWeight: 'bold', textTransform: 'none', fontSize: '1rem' }} />
                    ))}
                </Tabs>

                <Box sx={{ p: { xs: 3, md: 5 } }}>
                    <Grid container spacing={4}>
                        <Grid item xs={12} md={5}>
                            <Box sx={{ pr: { md: 4 } }}>
                                {activeTab.isPdv ? (
                                    <React.Fragment>
                                        <Typography variant="h6" fontWeight="bold" gutterBottom>Integração com PDV</Typography>
                                        <Typography variant="body2" color="text.secondary" paragraph>
                                            Importe Produtos e Vendas do seu sistema de caixa automaticamente.
                                        </Typography>
                                        <Alert severity="info" icon={<LightbulbOutlined />} sx={{ borderRadius: 2 }}>
                                            Recomendamos sincronizar no fim do dia para capturar todas as transações.
                                        </Alert>
                                    </React.Fragment>
                                ) : (
                                    <React.Fragment>
                                        <Typography variant="h6" fontWeight="bold" gutterBottom>Preparando o Arquivo</Typography>
                                        <Typography variant="body2" color="text.secondary" paragraph>
                                            O arquivo <strong>{activeTab.title}</strong> precisa ser <code>.csv</code> e conter estas colunas:
                                        </Typography>
                                        
                                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 3 }}>
                                            {activeTab.columns.map(col => (
                                                <Chip key={col} label={col} size="small" variant="outlined" sx={{ bgcolor: 'background.default', fontFamily: 'monospace' }} />
                                            ))}
                                        </Box>

                                        <Button 
                                            variant="outlined" 
                                            startIcon={<Description />}
                                            href={`/templates/${activeTab.templateName}`}
                                            download={activeTab.templateName}
                                            sx={{ borderRadius: 2, textTransform: 'none' }}
                                        >
                                            Baixar Template ({activeTab.templateName})
                                        </Button>
                                    </React.Fragment>
                                )}
                            </Box>
                        </Grid>

                        <Grid item xs={12} md={7}>
                            {activeTab.isPdv ? (
                                <Card variant="outlined" sx={{ borderRadius: 3, borderStyle: 'dashed', borderWidth: 2, borderColor: 'divider', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'background.default' }}>
                                    <CardContent sx={{ textAlign: 'center', p: 4 }}>
                                        <Sync sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                                        <Typography variant="h6" gutterBottom>Sincronização Remota</Typography>
                                        <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
                                            Injete os dados mais recentes do seu PDV no LucroPlus.
                                        </Typography>
                                        <Button 
                                            variant="contained" 
                                            size="large"
                                            onClick={handlePdvSync} 
                                            disabled={loading}
                                            startIcon={loading ? <CircularProgress size={20} color="inherit"/> : <Sync />}
                                            sx={{ py: 1.5, px: 4, borderRadius: 2, fontWeight: 'bold' }}
                                        >
                                            {loading ? 'Sincronizando...' : 'Iniciar Sincronização'}
                                        </Button>
                                    </CardContent>
                                </Card>
                            ) : (
                                <Box 
                                    onDragOver={handleDragOver}
                                    onDragEnter={handleDragEnter}
                                    onDragLeave={handleDragLeave}
                                    onDrop={handleDrop}
                                    sx={{
                                        border: '2px dashed',
                                        borderColor: isDragging ? 'primary.main' : 'divider',
                                        bgcolor: isDragging ? 'primary.50' : (file ? 'background.paper' : 'background.default'),
                                        borderRadius: 3,
                                        p: 4,
                                        height: '100%',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        textAlign: 'center',
                                        transition: 'all 0.2s ease',
                                        position: 'relative'
                                    }}
                                >
                                    <input type="file" accept=".csv" ref={fileInputRef} style={{ display: 'none' }} onChange={handleFileChange} />
                                    
                                    {!file ? (
                                        <React.Fragment>
                                            <CloudUpload sx={{ fontSize: 64, color: isDragging ? 'primary.main' : 'text.disabled', mb: 2 }} />
                                            <Typography variant="h6" gutterBottom>Arraste seu CSV para cá</Typography>
                                            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>ou clique para selecionar do seu computador</Typography>
                                            <Button variant="contained" onClick={() => fileInputRef.current.click()} sx={{ borderRadius: 2, textTransform: 'none' }}>
                                                Procurar Arquivo
                                            </Button>
                                        </React.Fragment>
                                    ) : (
                                        <Box sx={{ width: '100%' }}>
                                            <Description sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
                                            <Typography variant="h6" gutterBottom>{file.name}</Typography>
                                            <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
                                                {(file.size / 1024).toFixed(1)} KB
                                            </Typography>
                                            <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
                                                <Button variant="outlined" onClick={() => setFile(null)} disabled={loading} sx={{ borderRadius: 2 }}>
                                                    Trocar
                                                </Button>
                                                <Button 
                                                    variant="contained" 
                                                    onClick={handleUpload} 
                                                    disabled={loading}
                                                    startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <CloudUpload />}
                                                    sx={{ borderRadius: 2 }}
                                                >
                                                    {loading ? 'Enviando...' : 'Fazer Upload'}
                                                </Button>
                                            </Box>
                                        </Box>
                                    )}
                                </Box>
                            )}
                        </Grid>
                    </Grid>
                </Box>
            </Paper>

            {result && (
                <Paper sx={{ p: 4, borderRadius: 3, borderLeft: '6px solid', borderColor: result.sucesso ? 'success.main' : 'error.main', boxShadow: '0 4px 20px rgba(0,0,0,0.05)' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
                        {result.sucesso ? <CheckCircle color="success" sx={{ fontSize: 32, mr: 2 }}/> : <Error color="error" sx={{ fontSize: 32, mr: 2 }}/>}
                        <Typography variant="h5" fontWeight="bold" color={result.sucesso ? 'success.main' : 'error.main'}>
                            {result.mensagem}
                        </Typography>
                    </Box>

                    <Grid container spacing={3} sx={{ mb: 4 }}>
                        <Grid item xs={12} md={4}>
                            <Card variant="outlined" sx={{ bgcolor: 'background.default', textAlign: 'center', py: 2 }}>
                                <Typography variant="body2" color="text.secondary">Total Lido</Typography>
                                <Typography variant="h4" fontWeight="bold">{result.totalLinhas}</Typography>
                            </Card>
                        </Grid>
                        <Grid item xs={12} md={4}>
                            <Card variant="outlined" sx={{ bgcolor: 'success.50', borderColor: 'success.light', textAlign: 'center', py: 2 }}>
                                <Typography variant="body2" color="success.dark">Sucesso</Typography>
                                <Typography variant="h4" fontWeight="bold" color="success.main">{result.linhasSucesso}</Typography>
                            </Card>
                        </Grid>
                        <Grid item xs={12} md={4}>
                            <Card variant="outlined" sx={{ bgcolor: 'error.50', borderColor: 'error.light', textAlign: 'center', py: 2 }}>
                                <Typography variant="body2" color="error.dark">Erros</Typography>
                                <Typography variant="h4" fontWeight="bold" color="error.main">{result.linhasErro}</Typography>
                            </Card>
                        </Grid>
                    </Grid>

                    {result.erros && result.erros.length > 0 && (
                        <Box>
                            <Typography variant="h6" gutterBottom fontWeight="bold">Detalhamento dos Erros</Typography>
                            <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 400 }}>
                                <Table stickyHeader size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell sx={{ fontWeight: 'bold' }}>Linha</TableCell>
                                            <TableCell sx={{ fontWeight: 'bold' }}>Campo</TableCell>
                                            <TableCell sx={{ fontWeight: 'bold' }}>Valor Lido</TableCell>
                                            <TableCell sx={{ fontWeight: 'bold' }}>Motivo</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {result.erros.map((erro, idx) => (
                                            <TableRow key={idx} hover>
                                                <TableCell>#{erro.numeroLinha}</TableCell>
                                                <TableCell>{erro.campo}</TableCell>
                                                <TableCell>
                                                    <Chip label={erro.valor || '(Vazio)'} size="small" color="error" variant="outlined" />
                                                </TableCell>
                                                <TableCell>{erro.motivo}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Box>
                    )}
                </Paper>
            )}
        </Box>
    );
}



