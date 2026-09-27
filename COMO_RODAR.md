# 🚀 Guia de Execução — LucroPlus

> **Projeto:** LucroPlus — Gestão de Rentabilidade e Redução de Desperdício em Restaurantes  
> **Disciplina:** N393 — Projeto Aplicado Multiplataforma (PAM / Unifor)  
> **Orientadora:** Prof.ª Lyndainês Santos  
> **Equipe:** João Victor (Backend), Pedro Gabriel (Mobile/DBA), Emanuel Sales (QA/PM)

Este guia orienta o passo a passo para inicializar o sistema completo: Banco de Dados MySQL, API Backend em Kotlin/Ktor e Aplicativo Mobile em React Native/Expo.

---

## 📋 1. Pré-Requisitos

Para executar o projeto localmente, certifique-se de ter instalado:

1. **MySQL Server 8.x** (porta padrão `3306`)
2. **Java JDK 17** (LTS)
3. **Node.js** (versão 18 ou superior) e npm
4. **Expo Go** instalado no smartphone (disponível na Google Play e App Store) para testes no dispositivo físico

---

## ⚡ 2. Passo a Passo para Executar o Sistema

### Passo 1: Iniciar o Banco de Dados MySQL
Certifique-se de que o serviço do MySQL está em execução. No Windows (PowerShell):
```powershell
Get-Service -Name '*mysql*'
```
Caso esteja parado, inicie o serviço correspondente (ex: `Start-Service MySQL_Unifor_JV` ou `Start-Service MySQL80`).

> O banco de dados padrão é `lucroplus_db`. As tabelas e dados iniciais são gerenciados e estruturados pelo backend através do Exposed na primeira inicialização.

---

### Passo 2: Iniciar o Backend Ktor (API REST)
Abra um terminal na pasta `backend/`:

```powershell
cd backend

# Configurar o JDK 17
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Definir as configurações de conexão e segurança
$env:SERVER_HOST = '0.0.0.0'
$env:SERVER_PORT = '8080'
$env:DB_USER = 'root'
$env:DB_PASSWORD = 'SUA_SENHA_LOCAL'
$env:JWT_SECRET = 'SUA_CHAVE_JWT_SEGURA_COM_PELO_MENOS_32_CARACTERES'

# Compilar e iniciar a aplicação
.\mvnw.cmd compile exec:java
```

O servidor Ktor subirá na porta **8080**.

**Teste de Verificação:**
Abra `http://127.0.0.1:8080/` no navegador ou execute no terminal:
```powershell
Invoke-RestMethod 'http://127.0.0.1:8080/'
```
Retorno esperado:
```json
{
  "status": "ok",
  "mensagem": "LucroPlus Backend Ktor rodando com sucesso! 1.0.0"
}
```

---

### Passo 3: Iniciar o Aplicativo Mobile (Expo / React Native)

#### 3.1 Obter o IP Local (LAN) da Máquina
No terminal:
```powershell
ipconfig
```
Copie o endereço IPv4 da sua conexão local (exemplo: `192.168.1.149`).

#### 3.2 Configurar o arquivo `.env.local`
Na pasta `mobile/`, crie o arquivo `.env.local` (baseando-se em `.env.example`):
```env
EXPO_PUBLIC_API_URL=http://192.168.1.149:8080
```
*(Substitua pelo IPv4 obtido no comando anterior)*

#### 3.3 Iniciar o Expo
No terminal, dentro da pasta `mobile/`:
```powershell
cd mobile
npm install    # apenas na primeira execução
npx expo start --lan --clear
```

#### 3.4 Conectar e Usar
- **No Celular Físico:** Certifique-se de que o computador e o smartphone estão conectados na **mesma rede Wi-Fi**. Abra o **Expo Go** e escaneie o QR Code exibido no terminal.
- **No Navegador Web:** No terminal do Expo, pressione a tecla **`w`** para abrir o Web Preview.
- **Credenciais de Teste Padrão:**
  - **Email:** `gerente@LucroPlus.com`
  - **Senha:** `123`

---

### Passo 4 (Opcional): Iniciar o Frontend Web Legado (React + Vite)
Para inspecionar o painel administrativo web de referência desenvolvido na Sprint 1:
```powershell
cd frontend
npm install    # apenas na primeira execução
npm run dev
```
O servidor Vite estará disponível em: `http://localhost:5173`

---

## 🛠️ 3. Resolução Rápida de Dúvidas

- **Erro de versão do Java no backend:** Verifique se o `JAVA_HOME` está apontando para o JDK 17. O uso do Java 25 global causará erro de compatibilidade de bytecode no compilador do Kotlin.
- **Aplicativo mobile não conecta com o backend ("Network request failed"):**
  1. Verifique se o smartphone e o computador estão na mesma rede Wi-Fi local.
  2. Garanta que o backend foi iniciado com `SERVER_HOST=0.0.0.0`.
  3. No navegador do smartphone, tente acessar `http://<SEU_IP_LAN>:8080/`. Se não responder, confira as regras de entrada do Firewall do Windows para a porta 8080.
- **Porta 8080 ocupada:** Utilize `netstat -ano -p tcp | Select-String ':8080'` para identificar o processo e finalize a instância anterior antes de reiniciar.
