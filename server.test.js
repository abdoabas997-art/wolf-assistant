const test = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');
const { createApp } = require('../server');

test('health endpoint returns ok', async () => {
  const res = await request(createApp({ env: {} })).get('/health');
  assert.equal(res.status, 200);
  assert.equal(res.body.ok, true);
});

test('chat rejects an empty message', async () => {
  const res = await request(createApp({ env: { WOLF_AI_API_KEY: 'not-used' } })).post('/chat').send({ message: '  ' });
  assert.equal(res.status, 400);
});

test('chat reports missing provider configuration without exposing secrets', async () => {
  const res = await request(createApp({ env: {} })).post('/chat').send({ message: 'مرحبا' });
  assert.equal(res.status, 503);
  assert.equal(JSON.stringify(res.body).includes('API_KEY'), false);
});

test('chat returns provider response on success', async () => {
  const fakeFetch = async (_url, options) => {
    assert.equal(options.headers.Authorization, 'Bearer server-only-key');
    return { ok: true, json: async () => ({ choices: [{ message: { content: 'أهلًا من الذئب' } }] }) };
  };
  const app = createApp({ env: { WOLF_AI_API_KEY: 'server-only-key' }, fetchImpl: fakeFetch });
  const res = await request(app).post('/chat').send({ message: 'مرحبا', history: [] });
  assert.equal(res.status, 200);
  assert.equal(res.body.reply, 'أهلًا من الذئب');
});
