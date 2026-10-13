
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { createApp } = require('../server');

test('health endpoint returns ok', async () => {
  const res = await request(createApp({ env: {} })).get('/health');

  assert.equal(res.status, 200);
  assert.equal(res.body.ok, true);
  assert.equal(res.body.service, 'wolf-assistant-backend');
});

test('chat rejects an empty message', async () => {
  const app = createApp({
    env: { WOLF_AI_API_KEY: 'not-used' }
  });

  const res = await request(app)
    .post('/chat')
    .send({ message: '  ' });

  assert.equal(res.status, 400);
});

test('chat reports missing provider configuration', async () => {
  const app = createApp({ env: {} });

  const res = await request(app)
    .post('/chat')
    .send({ message: 'مرحبا' });

  assert.equal(res.status, 503);
  assert.equal(JSON.stringify(res.body).includes('API_KEY'), false);
});

test('chat returns provider response on success', async () => {
  const fakeFetch = async (url, options) => {
    assert.equal(
      url,
      'https://api.openai.com/v1/chat/completions'
    );

    assert.equal(
      options.headers.Authorization,
      'Bearer server-only-key'
    );

    return {
      ok: true,
      json: async () => ({
        choices: [
          {
            message: {
              content: 'أهلًا من الذئب'
            }
          }
        ]
      })
    };
  };

  const app = createApp({
    env: { WOLF_AI_API_KEY: 'server-only-key' },
    fetchImpl: fakeFetch
  });

  const res = await request(app)
    .post('/chat')
    .send({ message: 'مرحبا', history: [] });

  assert.equal(res.status, 200);
  assert.equal(res.body.reply, 'أهلًا من الذئب');
  assert.equal(res.body.model, 'gpt-4o-mini');
});
