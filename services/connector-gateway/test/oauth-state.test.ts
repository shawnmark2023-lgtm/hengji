import assert from "node:assert/strict";
import { test } from "node:test";
import { OAuthStateStore } from "../src/oauth-state.js";

test("OAuth state is one-time, bound to connector and exact redirect URI", () => {
  const store = new OAuthStateStore(60_000, () => 1000);
  const started = store.create("alipay-sandbox", "hengji://oauth/callback");

  assert.equal(store.consume("wechat-pay-sandbox", started.state, "hengji://oauth/callback"), undefined);
  assert.equal(store.consume("alipay-sandbox", started.state, "hengji://wrong"), undefined);
  assert.equal(store.consume("alipay-sandbox", started.state, "hengji://oauth/callback")?.flowId, started.flowId);
  assert.equal(store.consume("alipay-sandbox", started.state, "hengji://oauth/callback"), undefined);
});

test("OAuth state expires", () => {
  let now = 1000;
  const store = new OAuthStateStore(100, () => now);
  const started = store.create("alipay-sandbox", "hengji://oauth/callback");
  now += 101;

  assert.equal(store.consume("alipay-sandbox", started.state, "hengji://oauth/callback"), undefined);
  assert.equal(store.size, 0);
});
