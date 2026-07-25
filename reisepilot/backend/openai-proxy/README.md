# ReisePilot OpenAI Proxy

Der Android-Client enthält absichtlich **keinen OpenAI-API-Schlüssel**. Dieses kleine Node-20-Backend hält den Schlüssel serverseitig und gibt ausschließlich das strukturierte ReisePilot-Antwortformat zurück.

## Umgebungsvariablen

```bash
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4.1-mini
REISEPILOT_ACCESS_TOKEN=ein-langer-eigener-zugangscode
PORT=8787
MAX_REQUESTS_PER_HOUR=30
```

`OPENAI_API_KEY` und `REISEPILOT_ACCESS_TOKEN` niemals in Git eintragen.

## Start

```bash
cd reisepilot/backend/openai-proxy
npm start
```

Für die Android-App wird eine öffentlich erreichbare **HTTPS-Adresse** benötigt. In ReisePilot unter `Reise-Assistent → OpenAI-Backend einrichten` nur die Proxy-Adresse und den optionalen Zugangscode eintragen.

## Endpunkte

- `GET /health`
- `POST /assistant`

Der Proxy verwendet die OpenAI Responses API mit strengem JSON-Schema, `store: false`, Größenbegrenzung und einfacher IP-Ratenbegrenzung. Er akzeptiert keine direkten App-Aktionen; Navigation und Packlistenänderungen müssen weiterhin auf dem Gerät bestätigt werden.
