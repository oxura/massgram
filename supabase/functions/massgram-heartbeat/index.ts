import { corsHeaders } from "../_shared/cors.ts";
import { supabaseAdmin } from "../_shared/supabase-admin.ts";

type HeartbeatPayload = {
  user_id?: number;
  username?: string | null;
  first_name?: string | null;
  last_name?: string | null;
  app_version?: string | null;
  version_code?: number | null;
  package_name?: string | null;
  build_channel?: string | null;
  device_model?: string | null;
  os_version?: string | null;
  locale?: string | null;
};

type HeartbeatBatchPayload = {
  users?: HeartbeatPayload[];
};

function trimValue(value: string | null | undefined, maxLength: number): string | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim().replace(/\s+/g, " ");
  if (!trimmed) {
    return null;
  }
  return trimmed.slice(0, maxLength);
}

function buildDisplayName(firstName: string | null, lastName: string | null, username: string | null, userId: number): string {
  const parts = [firstName, lastName].filter(Boolean);
  if (parts.length > 0) {
    return parts.join(" ").slice(0, 128);
  }
  if (username) {
    return username;
  }
  return String(userId);
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
    const body = await req.json() as HeartbeatPayload | HeartbeatBatchPayload;
    const payloads = Array.isArray((body as HeartbeatBatchPayload).users)
      ? (body as HeartbeatBatchPayload).users ?? []
      : [body as HeartbeatPayload];
    if (payloads.length == 0) {
      return new Response(JSON.stringify({ error: "Invalid user_id" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const now = new Date().toISOString();
    const rows = payloads
      .map((payload) => {
        const userId = Number(payload.user_id ?? 0);
        if (!Number.isFinite(userId) || userId <= 0) {
          return null;
        }
        const username = trimValue(payload.username, 64);
        const firstName = trimValue(payload.first_name, 64);
        const lastName = trimValue(payload.last_name, 64);
        const appVersion = trimValue(payload.app_version, 64);
        const packageName = trimValue(payload.package_name, 128);
        const deviceModel = trimValue(payload.device_model, 128);
        const osVersion = trimValue(payload.os_version, 64);
        const locale = trimValue(payload.locale, 32);
        const buildChannel = payload.build_channel === "beta" ? "beta" : "stable";
        const versionCode = Number.isFinite(Number(payload.version_code)) ? Number(payload.version_code) : null;
        return {
          user_id: userId,
          username,
          first_name: firstName,
          last_name: lastName,
          display_name: buildDisplayName(firstName, lastName, username, userId),
          app_version: appVersion,
          version_code: versionCode,
          package_name: packageName,
          build_channel: buildChannel,
          device_model: deviceModel,
          os_version: osVersion,
          locale,
          last_seen_at: now,
          last_heartbeat_at: now,
        };
      })
      .filter(Boolean);
    if (rows.length == 0) {
      return new Response(JSON.stringify({ error: "Invalid user_id" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { error } = await supabaseAdmin
      .from("massgram_clients")
      .upsert(rows, { onConflict: "user_id" });

    if (error) {
      console.error("heartbeat upsert failed", error);
      return new Response(JSON.stringify({ error: "Failed to store heartbeat" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ ok: true, updated: rows.length }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("heartbeat failed", error);
    return new Response(JSON.stringify({ error: "Invalid heartbeat payload" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
