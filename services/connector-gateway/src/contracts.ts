export const sandboxConnectorIds = [
  "alipay-sandbox",
  "wechat-pay-sandbox",
  "taobao-sandbox",
  "jd-sandbox",
] as const;

export type SandboxConnectorId = (typeof sandboxConnectorIds)[number];

export interface ConnectorSummary {
  id: SandboxConnectorId;
  displayName: string;
  availability: "sandbox";
  authorizationMode: "oauth_pkce_simulation";
  capabilities: readonly ["transactions", "categories", "refunds"];
  live: false;
  disclosure: string;
}

export interface OAuthStartRequest {
  connectorId: SandboxConnectorId;
  redirectUri: string;
}

export interface OAuthStartResponse {
  flowId: string;
  connectorId: SandboxConnectorId;
  authorizationUrl: string;
  expiresAt: string;
  mode: "sandbox_no_token";
  disclosure: string;
}

export interface OAuthCallbackRequest {
  connectorId: SandboxConnectorId;
  state: string;
  code: string;
  redirectUri: string;
}

export interface OAuthCallbackResponse {
  connectorId: SandboxConnectorId;
  status: "authorized_sandbox";
  tokenStored: false;
  disclosure: string;
}

export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    requestId: string;
  };
}

export function isSandboxConnectorId(value: unknown): value is SandboxConnectorId {
  return typeof value === "string" && (sandboxConnectorIds as readonly string[]).includes(value);
}

export function requireNonEmptyString(value: unknown, field: string, maximum = 2048): string {
  if (typeof value !== "string" || value.length === 0 || value.length > maximum) {
    throw new RequestValidationError("INVALID_FIELD", `${field} must be a non-empty string no longer than ${maximum} characters`);
  }
  return value;
}

export class RequestValidationError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly statusCode = 400,
  ) {
    super(message);
    this.name = "RequestValidationError";
  }
}
