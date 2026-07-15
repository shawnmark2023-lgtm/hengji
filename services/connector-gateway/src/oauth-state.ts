import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import type { SandboxConnectorId } from "./contracts.js";

export interface PendingOAuthFlow {
  flowId: string;
  connectorId: SandboxConnectorId;
  stateHash: Buffer;
  verifier: string;
  redirectUri: string;
  expiresAtEpochMs: number;
}

export interface OAuthFlowStart {
  flowId: string;
  state: string;
  codeChallenge: string;
  expiresAtEpochMs: number;
}

export class OAuthStateStore {
  private readonly pending = new Map<string, PendingOAuthFlow>();

  constructor(
    private readonly ttlMs = 5 * 60_000,
    private readonly now: () => number = Date.now,
  ) {}

  create(connectorId: SandboxConnectorId, redirectUri: string): OAuthFlowStart {
    this.removeExpired();
    const flowId = randomBytes(16).toString("base64url");
    const state = randomBytes(32).toString("base64url");
    const verifier = randomBytes(64).toString("base64url");
    const expiresAtEpochMs = this.now() + this.ttlMs;
    this.pending.set(flowId, {
      flowId,
      connectorId,
      stateHash: digest(state),
      verifier,
      redirectUri,
      expiresAtEpochMs,
    });
    return {
      flowId,
      state,
      codeChallenge: digest(verifier).toString("base64url"),
      expiresAtEpochMs,
    };
  }

  consume(connectorId: SandboxConnectorId, state: string, redirectUri: string): PendingOAuthFlow | undefined {
    this.removeExpired();
    const incomingStateHash = digest(state);
    const entry = [...this.pending.values()].find(
      (candidate) => candidate.connectorId === connectorId
        && candidate.redirectUri === redirectUri
        && safeEqual(candidate.stateHash, incomingStateHash),
    );
    if (entry === undefined) return undefined;
    this.pending.delete(entry.flowId);
    return entry;
  }

  get size(): number {
    this.removeExpired();
    return this.pending.size;
  }

  private removeExpired(): void {
    const now = this.now();
    for (const [key, value] of this.pending) {
      if (value.expiresAtEpochMs <= now) this.pending.delete(key);
    }
  }
}

function digest(value: string): Buffer {
  return createHash("sha256").update(value, "utf8").digest();
}

function safeEqual(left: Buffer, right: Buffer): boolean {
  return left.length === right.length && timingSafeEqual(left, right);
}
