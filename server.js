'use strict';
const express = require('express');

function createApp({ env = process.env, fetchImpl = global.fetch } = {}) {
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '64kb' }));

  // Small in-memory rate limit to reduce accidental bursts. Use a managed limiter for production scale.
  const buckets = new Map();
  app.use((req, res, next) => {
    if (req.path !== '/chat' || req.method !== 'POST') return next();
    const now = Date.now();
    const key = req.ip || req.socket.remoteAddress || 'unknown';
    const bucket = buckets.get(key) || { count: 0, reset: now + 60_000 };
    if (now > bucket.reset) { bucket.count = 0; bucket.reset = now + 60_000; }
    bucket.count += 1; buckets.set(key, bucket);
    if (bucket.count > 20) return res.status(429).json({ error: 'rate limit exceeded' });
    next();
  });

  app.get('/health', (_req, res) => res.json({ ok: true, service: 'wolf-assistant-backend' }));

  app.post('/chat', async (req, res) => {
    const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
    if (!message) return res.status(400).json({ error: 'message is required' });
    if (message.length > 4000) return res.status(413).json({ error: 'message too long' });

    const apiKey = env.WOLF_AI_API_KEY;
    const baseUrl = (env.WOLF_AI_BASE_URL || 'https://api.openai.com/v1').replace(/\/+$/, '');
    const model = env.WOLF_AI_MODEL || 'gpt-4o-mini';
    if (!apiKey) return res.status(503).json({ error: 'AI provider is not configured' });
    if (!baseUrl.startsWith('https://')) return res.status(500).json({ error: 'AI provider URL must use HTTPS' });

    const history = Array.isArray(req.body?.history) ? req.body.history.slice(-12) : [];
    const messages = [{ role: 'system', content: 'أنت مساعد الذئب، مساعد عربي عملي ومحترم. أجب بوضوح، ولا تدّع تنفيذ إجراء على الهاتف ما لم تؤكده أداة موثوقة.' }];
    for (const item of history) {
      if (!item || !['user', 'assistant'].includes(item.role) || typeof item.content !== 'string') continue;
      const content = item.content.trim().slice(0, 4000);
      if (content) messages.push({ role: item.role, content });
    }
    // Ensure the current user message is the last message, without duplicating it if included in history.
    if (messages[messages.length - 1]?.role !== 'user' || messages[messages.length - 1]?.content !== message) {
      messages.push({ role: 'user', content: message });
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 40_000);
    try {
      const upstream = await fetchImpl(`${baseUrl}/chat/completions`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model, messages, temperature: 0.7 }),
        signal: controller.signal
      });
      if (!upstream.ok) {
        // Do not return upstream body or secrets to the client.
        const status = upstream.status === 429 ? 429 : 502;
        return res.status(status).json({ error: 'AI provider request failed' });
      }
      const data = await upstream.json();
      const reply = data?.choices?.[0]?.message?.content;
      if (typeof reply !== 'string' || !reply.trim()) return res.status(502).json({ error: 'AI provider returned an empty response' });
      return res.json({ reply: reply.trim(), model });
    } catch (_err) {
      return res.status(502).json({ error: 'AI provider is unavailable' });
    } finally {
      clearTimeout(timeout);
    }
  });

  app.use((err, _req, res, _next) => {
    if (err?.type === 'entity.too.large') return res.status(413).json({ error: 'request too large' });
    if (err instanceof SyntaxError) return res.status(400).json({ error: 'invalid JSON' });
    return res.status(500).json({ error: 'internal server error' });
  });
  return app;
}

if (require.main === module) {
  const app = createApp();
  const port = Number(process.env.PORT || 3000);
  app.listen(port, '0.0.0.0', () => console.log(`Wolf Assistant backend listening on ${port}`));
}
module.exports = { createApp };
