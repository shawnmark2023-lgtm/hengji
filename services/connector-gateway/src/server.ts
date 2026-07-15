import { createServer as createHttpServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { randomBytes } from "node:crypto";
import {
  isSandboxConnectorId,
  type ApiErrorBody,
  type ConnectorSummary,
  type OAuthCallbackResponse,
  type OAuthStartResponse,
  RequestValidationError,
  requireNonEmptyString,
  sandboxConnectorIds,
} from "./contracts.js";
import { OAuthStateStore } from "./oauth-state.js";

const DISCLOSURE = "沙箱演示流程，非实时、非真实账户同步，不保存生产 token。";
const MAX_BODY_BYTES = 64 * 1024;

export interface GatewayConfig {
  mode: "sandbox" | "production";
  allowedRedirectUris: ReadonlySet<string>;
  stateStore?: OAuthStateStore;
  publicBaseUrl?: string;
}

const connectorNames: Record<(typeof sandboxConnectorIds)[number], string> = {
  "alipay-sandbox": "支付宝（沙箱）",
  "wechat-pay-sandbox": "微信支付（沙箱）",
  "taobao-sandbox": "淘宝（沙箱）",
  "jd-sandbox": "京东（沙箱）",
};

export function createGatewayServer(config: GatewayConfig): Server {
  if (config.mode === "production") {
    throw new Error("Production connector access is fail-closed: token vault and reviewed provider registry are not configured");
  }
  const stateStore = config.stateStore ?? new OAuthStateStore();
  return createHttpServer(async (request, response) => {
    const requestId = randomBytes(12).toString("hex");
    setSecurityHeaders(response, requestId);
    try {
      await route(request, response, config, stateStore, requestId);
    } catch (error) {
      handleError(error, response, requestId);
    }
  });
}

async function route(
  request: IncomingMessage,
  response: ServerResponse,
  config: GatewayConfig,
  stateStore: OAuthStateStore,
  requestId: string,
): Promise<void> {
  const url = new URL(request.url ?? "/", "http://gateway.invalid");
  if (request.method === "GET" && url.pathname === "/health") {
    writeJson(response, 200, { status: "ok", mode: config.mode, requestId });
    return;
  }
  if (request.method === "GET" && url.pathname === "/v1/connectors") {
    const connectors: ConnectorSummary[] = sandboxConnectorIds.map((id) => ({
      id,
      displayName: connectorNames[id],
      availability: "sandbox",
      authorizationMode: "oauth_pkce_simulation",
      capabilities: ["transactions", "categories", "refunds"],
      live: false,
      disclosure: DISCLOSURE,
    }));
    writeJson(response, 200, { connectors, requestId });
    return;
  }
  if (request.method === "POST" && url.pathname === "/v1/oauth/sandbox/start") {
    const body = await readJsonObject(request);
    const connectorId = body.connectorId;
    if (!isSandboxConnectorId(connectorId)) throw new RequestValidationError("UNKNOWN_CONNECTOR", "Unknown sandbox connector");
    const redirectUri = requireNonEmptyString(body.redirectUri, "redirectUri", 512);
    requireAllowedRedirect(config.allowedRedirectUris, redirectUri);
    const flow = stateStore.create(connectorId, redirectUri);
    const authorizationUrl = new URL("/v1/oauth/sandbox/consent", config.publicBaseUrl ?? "http://127.0.0.1:8787");
    authorizationUrl.searchParams.set("connector_id", connectorId);
    authorizationUrl.searchParams.set("redirect_uri", redirectUri);
    authorizationUrl.searchParams.set("response_type", "code");
    authorizationUrl.searchParams.set("state", flow.state);
    authorizationUrl.searchParams.set("code_challenge", flow.codeChallenge);
    authorizationUrl.searchParams.set("code_challenge_method", "S256");
    const result: OAuthStartResponse = {
      flowId: flow.flowId,
      connectorId,
      authorizationUrl: authorizationUrl.toString(),
      expiresAt: new Date(flow.expiresAtEpochMs).toISOString(),
      mode: "sandbox_no_token",
      disclosure: DISCLOSURE,
    };
    writeJson(response, 201, result);
    return;
  }
  if (request.method === "POST" && url.pathname === "/v1/oauth/sandbox/callback") {
    const body = await readJsonObject(request);
    const connectorId = body.connectorId;
    if (!isSandboxConnectorId(connectorId)) throw new RequestValidationError("UNKNOWN_CONNECTOR", "Unknown sandbox connector");
    const state = requireNonEmptyString(body.state, "state", 256);
    requireNonEmptyString(body.code, "code", 512);
    const redirectUri = requireNonEmptyString(body.redirectUri, "redirectUri", 512);
    requireAllowedRedirect(config.allowedRedirectUris, redirectUri);
    const flow = stateStore.consume(connectorId, state, redirectUri);
    if (flow === undefined) throw new RequestValidationError("INVALID_OR_EXPIRED_STATE", "OAuth state is invalid, expired, or already used", 400);
    const result: OAuthCallbackResponse = {
      connectorId,
      status: "authorized_sandbox",
      tokenStored: false,
      disclosure: DISCLOSURE,
    };
    writeJson(response, 200, result);
    return;
  }
  throw new RequestValidationError("NOT_FOUND", "Route not found", 404);
}

async function readJsonObject(request: IncomingMessage): Promise<Record<string, unknown>> {
  if (!(request.headers["content-type"] ?? "").toLowerCase().startsWith("application/json")) {
    throw new RequestValidationError("UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", 415);
  }
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunkValue of request) {
    const chunk = Buffer.isBuffer(chunkValue) ? chunkValue : Buffer.from(chunkValue as Uint8Array);
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new RequestValidationError("BODY_TOO_LARGE", "Request body exceeds 65536 bytes", 413);
    chunks.push(chunk);
  }
  let value: unknown;
  try {
    value = JSON.parse(Buffer.concat(chunks).toString("utf8")) as unknown;
  } catch {
    throw new RequestValidationError("INVALID_JSON", "Request body is not valid JSON");
  }
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new RequestValidationError("INVALID_JSON_OBJECT", "Request body must be a JSON object");
  }
  return value as Record<string, unknown>;
}

function requireAllowedRedirect(allowed: ReadonlySet<string>, value: string): void {
  if (!allowed.has(value)) {
    throw new RequestValidationError("REDIRECT_URI_NOT_ALLOWED", "redirectUri must exactly match a pre-registered URI");
  }
}

function setSecurityHeaders(response: ServerResponse, requestId: string): void {
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
  response.setHeader("Referrer-Policy", "no-referrer");
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.setHeader("X-Request-Id", requestId);
}

function writeJson(response: ServerResponse, statusCode: number, value: unknown): void {
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.end(JSON.stringify(value));
}

function handleError(error: unknown, response: ServerResponse, requestId: string): void {
  const known = error instanceof RequestValidationError;
  const statusCode = known ? error.statusCode : 500;
  const body: ApiErrorBody = {
    error: {
      code: known ? error.code : "INTERNAL_ERROR",
      message: known ? error.message : "Request could not be completed",
      requestId,
    },
  };
  writeJson(response, statusCode, body);
}

if (require.main === module) {
  const port = Number.parseInt(process.env.PORT ?? "8787", 10);
  const allowed = new Set((process.env.ALLOWED_REDIRECT_URIS ?? "hengji://oauth/callback").split(","));
  const server = createGatewayServer({
    mode: "sandbox",
    allowedRedirectUris: allowed,
    publicBaseUrl: process.env.PUBLIC_BASE_URL ?? `http://127.0.0.1:${port}`,
  });
  server.listen(port, "127.0.0.1", () => {
    process.stdout.write(`HENGJI connector gateway sandbox listening on 127.0.0.1:${port}\n`);
  });
}
