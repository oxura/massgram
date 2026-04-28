import { corsHeaders } from "../_shared/cors.ts";
import { supabaseAdmin } from "../_shared/supabase-admin.ts";

type TelemetryEventPayload = {
  event_id?: string;
  user_id?: number;
  event_type?: string | null;
  severity?: string | null;
  fingerprint?: string | null;
  title?: string | null;
  screen?: string | null;
  dialog_id?: number | null;
  app_version?: string | null;
  version_code?: number | null;
  build_channel?: string | null;
  device_model?: string | null;
  os_version?: string | null;
  breadcrumbs?: unknown[];
  stacktrace?: unknown[];
  context?: Record<string, unknown> | null;
  occurred_at?: number | string | null;
};

type EventBatchPayload = {
  events?: TelemetryEventPayload[];
};

const MAX_BATCH = 50;
const DEDUPE_WINDOW_MS = 5 * 60 * 1000;

function normalizeString(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const normalized = value.trim().replace(/\s+/g, " ");
  if (!normalized) {
    return null;
  }
  return normalized.slice(0, maxLength);
}

function normalizeSeverity(value: unknown): string {
  return value === "fatal" || value === "warning" ? String(value) : "error";
}

function normalizeBuildChannel(value: unknown): string {
  return value === "beta" ? "beta" : "stable";
}

function normalizeOccurredAt(value: unknown): string {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return new Date(value).toISOString();
  }
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    if (Number.isFinite(parsed)) {
      return new Date(parsed).toISOString();
    }
  }
  return new Date().toISOString();
}

function normalizeJsonArray(value: unknown, maxItems: number): unknown[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.slice(0, maxItems);
}

function normalizeJsonObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as Record<string, unknown>;
}

function pick(payload: Record<string, unknown>, snakeKey: string, camelKey: string): unknown {
  return payload[snakeKey] ?? payload[camelKey];
}

function buildDedupeKey(userId: number, severity: string, fingerprint: string, occurredAtIso: string): string {
  const bucket = Math.floor(new Date(occurredAtIso).getTime() / DEDUPE_WINDOW_MS);
  return `${userId}:${severity}:${fingerprint}:${bucket}`.slice(0, 512);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const body = await req.json() as TelemetryEventPayload | EventBatchPayload;
    const payloads = Array.isArray((body as EventBatchPayload).events)
      ? ((body as EventBatchPayload).events ?? [])
      : [body as TelemetryEventPayload];

    const rows = payloads
      .slice(0, MAX_BATCH)
      .map((payload) => {
        const row = payload as Record<string, unknown>;
        const userId = Number(pick(row, "user_id", "userId") ?? 0);
        const fingerprint = normalizeString(pick(row, "fingerprint", "fingerprint"), 255);
        if (!Number.isFinite(userId) || userId <= 0 || !fingerprint) {
          return null;
        }
        const occurredAt = normalizeOccurredAt(pick(row, "occurred_at", "occurredAt"));
        const severity = normalizeSeverity(pick(row, "severity", "severity"));
        return {
          event_id: normalizeString(pick(row, "event_id", "eventId"), 64) ?? crypto.randomUUID(),
          dedupe_key: buildDedupeKey(userId, severity, fingerprint, occurredAt),
          user_id: userId,
          event_type: normalizeString(pick(row, "event_type", "eventType"), 48) ?? "handled_error",
          severity,
          fingerprint,
          title: normalizeString(pick(row, "title", "title"), 160),
          screen: normalizeString(pick(row, "screen", "screen"), 64),
          dialog_id: Number.isFinite(Number(pick(row, "dialog_id", "dialogId"))) ? Number(pick(row, "dialog_id", "dialogId")) : null,
          app_version: normalizeString(pick(row, "app_version", "appVersion"), 64),
          version_code: Number.isFinite(Number(pick(row, "version_code", "versionCode"))) ? Number(pick(row, "version_code", "versionCode")) : null,
          build_channel: normalizeBuildChannel(pick(row, "build_channel", "buildChannel")),
          device_model: normalizeString(pick(row, "device_model", "deviceModel"), 128),
          os_version: normalizeString(pick(row, "os_version", "osVersion"), 64),
          breadcrumbs_json: normalizeJsonArray(pick(row, "breadcrumbs", "breadcrumbs"), 20),
          stacktrace_json: normalizeJsonArray(pick(row, "stacktrace", "stacktrace"), 20),
          context_json: normalizeJsonObject(pick(row, "context", "context")),
          occurred_at: occurredAt,
          received_at: new Date().toISOString(),
        };
      })
      .filter(Boolean);

    if (rows.length === 0) {
      return new Response(JSON.stringify({ error: "Invalid telemetry payload" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { error } = await supabaseAdmin
      .from("massgram_client_events")
      .upsert(rows, { onConflict: "dedupe_key" });

    if (error) {
      console.error("telemetry upsert failed", error);
      return new Response(JSON.stringify({ error: "Failed to store telemetry" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ ok: true, accepted: rows.length }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("telemetry ingestion failed", error);
    return new Response(JSON.stringify({ error: "Invalid telemetry payload" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
