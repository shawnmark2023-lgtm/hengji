import assert from "node:assert/strict";
import { test } from "node:test";
import { once } from "node:events";
import type { AddressInfo } from "node:net";
import { createGatewayServer } from "../src/server.js";

test("reports every connector as non-live sandbox", async (context) => {
  const server = createGatewayServer({
    mode: "sandbox",
    allowedRedirectUris: new Set(["hengji://oauth/callback"]),
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => server.close());
  const port = (server.address() as AddressInfo).port;

  const response = await fetch(`http://127.0.0.1:${port}/v1/connectors`);
  const body = await response.json() as { connectors: Array<{ live: boolean; disclosure: string }> };

  assert.equal(response.status, 200);
  assert.ok(body.connectors.every((connector) => connector.live === false));
  assert.ok(body.connectors.every((connector) => connector.disclosure.includes("非实时")));
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("production mode is fail-closed", () => {
  assert.throws(
    () => createGatewayServer({ mode: "production", allowedRedirectUris: new Set() }),
    /fail-closed/,
  );
});
