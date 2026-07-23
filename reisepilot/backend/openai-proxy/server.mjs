import http from 'node:http';

const PORT = Number(process.env.PORT || 8787);
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || '';
const OPENAI_MODEL = process.env.OPENAI_MODEL || 'gpt-4.1-mini';
const ACCESS_TOKEN = process.env.REISEPILOT_ACCESS_TOKEN || '';
const MAX_BODY_BYTES = 256_000;
const WINDOW_MS = 60 * 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = Number(process.env.MAX_REQUESTS_PER_HOUR || 30);
const usage = new Map();

const schema = {
  type: 'object',
  additionalProperties: false,
  required: ['headline', 'summary', 'suggestions', 'warnings'],
  properties: {
    headline: { type: 'string', maxLength: 120 },
    summary: { type: 'string', maxLength: 500 },
    suggestions: {
      type: 'array',
      maxItems: 5,
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['title', 'detail', 'reason', 'destinationTitle', 'packingItems'],
        properties: {
          title: { type: 'string', maxLength: 120 },
          detail: { type: 'string', maxLength: 450 },
          reason: { type: 'string', maxLength: 450 },
          destinationTitle: { type: 'string', maxLength: 120 },
          packingItems: {
            type: 'array',
            maxItems: 12,
            items: { type: 'string', maxLength: 80 }
          }
        }
      }
    },
    warnings: {
      type: 'array',
      maxItems: 5,
      items: { type: 'string', maxLength: 220 }
    }
  }
};

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff'
  });
  res.end(body);
}

function authorized(req) {
  if (!ACCESS_TOKEN) return true;
  return req.headers.authorization === `Bearer ${ACCESS_TOKEN}`;
}

function rateAllowed(req) {
  const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  const key = forwarded || req.socket.remoteAddress || 'unknown';
  const now = Date.now();
  const current = usage.get(key);
  if (!current || now - current.startedAt >= WINDOW_MS) {
    usage.set(key, { startedAt: now, count: 1 });
    return true;
  }
  current.count += 1;
  return current.count <= MAX_REQUESTS_PER_WINDOW;
}

async function readJson(req) {
  const chunks = [];
  let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > MAX_BODY_BYTES) throw new Error('request_too_large');
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString('utf8');
  return JSON.parse(raw || '{}');
}

function sanitizeContext(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid_context');
  const serialized = JSON.stringify(value);
  if (serialized.length > 220_000) throw new Error('context_too_large');
  return value;
}

function destinationTitles(context) {
  const list = Array.isArray(context.destinations) ? context.destinations : [];
  return list.map(item => String(item?.title || '')).filter(Boolean);
}

function extractOutputText(response) {
  for (const item of response.output || []) {
    for (const content of item.content || []) {
      if (content.type === 'output_text' && typeof content.text === 'string') return content.text;
    }
  }
  throw new Error('openai_response_without_text');
}

async function askOpenAI(context) {
  if (!OPENAI_API_KEY) throw new Error('OPENAI_API_KEY_not_configured');
  const allowedDestinations = destinationTitles(context);
  const instructions = [
    'Du bist der ReisePilot-Copilot für eine vierköpfige Familie.',
    'Arbeite ausschließlich mit den gelieferten Daten. Erfinde kein Wetter, keine Öffnungszeiten, Preise, Staus oder Verfügbarkeiten.',
    'Antworte auf Deutsch, konkret und knapp. Maximal fünf Vorschläge.',
    'Änderungen an Navigation oder Packliste werden nur vorbereitet und niemals automatisch ausgeführt.',
    'destinationTitle muss leer sein oder exakt einem Titel aus der gelieferten Zielliste entsprechen.',
    'Bei fehlenden Daten benenne die Einschränkung in warnings.',
    `Erlaubte destinationTitle-Werte: ${allowedDestinations.join(' | ') || 'keine'}`
  ].join('\n');

  const openAIResponse = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST',
    headers: {
      authorization: `Bearer ${OPENAI_API_KEY}`,
      'content-type': 'application/json'
    },
    body: JSON.stringify({
      model: OPENAI_MODEL,
      store: false,
      max_output_tokens: 1600,
      instructions,
      input: JSON.stringify(context),
      text: {
        format: {
          type: 'json_schema',
          name: 'reisepilot_answer',
          strict: true,
          schema
        }
      }
    })
  });

  const payload = await openAIResponse.json().catch(() => ({}));
  if (!openAIResponse.ok) {
    const message = payload?.error?.message || `OpenAI HTTP ${openAIResponse.status}`;
    throw new Error(message);
  }
  const parsed = JSON.parse(extractOutputText(payload));
  const allowed = new Set(allowedDestinations);
  for (const suggestion of parsed.suggestions || []) {
    if (suggestion.destinationTitle && !allowed.has(suggestion.destinationTitle)) suggestion.destinationTitle = '';
  }
  return parsed;
}

const server = http.createServer(async (req, res) => {
  try {
    const path = new URL(req.url || '/', 'http://localhost').pathname;
    if (!authorized(req)) return json(res, 401, { error: 'Nicht autorisiert' });

    if (req.method === 'GET' && path === '/health') {
      return json(res, 200, {
        status: OPENAI_API_KEY ? 'bereit' : 'OpenAI-Key fehlt',
        model: OPENAI_MODEL,
        openaiConfigured: Boolean(OPENAI_API_KEY)
      });
    }

    if (req.method === 'POST' && path === '/assistant') {
      if (!rateAllowed(req)) return json(res, 429, { error: 'Stundenlimit erreicht' });
      const context = sanitizeContext(await readJson(req));
      const answer = await askOpenAI(context);
      return json(res, 200, answer);
    }

    return json(res, 404, { error: 'Nicht gefunden' });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unbekannter Fehler';
    const status = message === 'request_too_large' || message === 'context_too_large' ? 413 : 500;
    return json(res, status, { error: message.slice(0, 240) });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`ReisePilot OpenAI proxy listening on :${PORT}`);
});
