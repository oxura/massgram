import { corsHeaders } from "./_shared/cors.ts";
import { supabaseAdmin } from "./_shared/supabase-admin.ts";

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
        const userId = Number(payload.user_id ?? 0);
        const fingerprint = normalizeString(payload.fingerprint, 255);
        if (!Number.isFinite(userId) || userId <= 0 || !fingerprint) {
          return null;
        }
        const occurredAt = normalizeOccurredAt(payload.occurred_at);
        const severity = normalizeSeverity(payload.severity);
        return {
          event_id: normalizeString(payload.event_id, 64) ?? crypto.randomUUID(),
          dedupe_key: buildDedupeKey(userId, severity, fingerprint, occurredAt),
          user_id: userId,
          event_type: normalizeString(payload.event_type, 48) ?? "handled_error",
          severity,
          fingerprint,
          title: normalizeString(payload.title, 160),
          screen: normalizeString(payload.screen, 64),
          dialog_id: Number.isFinite(Number(payload.dialog_id)) ? Number(payload.dialog_id) : null,
          app_version: normalizeString(payload.app_version, 64),
          version_code: Number.isFinite(Number(payload.version_code)) ? Number(payload.version_code) : null,
          build_channel: normalizeBuildChannel(payload.build_channel),
          device_model: normalizeString(payload.device_model, 128),
          os_version: normalizeString(payload.os_version, 64),
          breadcrumbs_json: normalizeJsonArray(payload.breadcrumbs, 20),
          stacktrace_json: normalizeJsonArray(payload.stacktrace, 20),
          context_json: normalizeJsonObject(payload.context),
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
