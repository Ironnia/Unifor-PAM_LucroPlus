# Ambiente local para teste do app no celular

## Configurar a API

O app lê `EXPO_PUBLIC_API_URL`. Copie `mobile/.env.example` para `mobile/.env.local` e substitua `SEU_IP_LAN` pelo IPv4 do computador:

```powershell
ipconfig
```

Exemplo de configuração local:

```dotenv
EXPO_PUBLIC_API_URL=http://192.168.1.10:8080
```

Não versione `.env.local`. O endereço pode mudar quando o computador se reconecta à rede.

## Iniciar o backend

Use JDK 17, inicie o MySQL e execute:

```powershell
cd backend
.\mvnw.cmd exec:java
```

O Ktor usa `SERVER_HOST=0.0.0.0` e `SERVER_PORT=8080` por padrão. Essas variáveis podem ser sobrescritas sem recompilar.

Confirme no computador e no navegador do celular:

```text
http://SEU_IP_LAN:8080/
```

A resposta esperada contém `"status": "ok"`.

## Iniciar o Expo

```powershell
cd mobile
npm ci
npx expo start --lan --clear
```

O computador e o celular devem estar na mesma rede. Se o health check funcionar apenas no computador, verifique isolamento da rede e a entrada TCP 8080 no Firewall do Windows para redes privadas.

## Endereços por ambiente

| Ambiente | URL típica |
| :--- | :--- |
| Celular físico | `http://IP_LAN_DO_PC:8080` |
| Emulador Android | `http://10.0.2.2:8080` |
| Expo Web/iOS Simulator local | `http://localhost:8080` |
| Backend publicado | URL HTTPS fornecida pela hospedagem |
