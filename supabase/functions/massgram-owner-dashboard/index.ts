import { corsHeaders } from "../_shared/cors.ts";
import { supabaseAdmin } from "../_shared/supabase-admin.ts";

const OWNER_IDS = new Set(["6539627752"]);
const MAX_ROWS = 500;

function parseOwnerId(req: Request): string | null {
  const url = new URL(req.url);
  return req.headers.get("x-massgram-owner-id") ?? url.searchParams.get("owner_id");
}

function escapeLike(value: string): string {
  return value.replace(/[%_]/g, (match) => `\\${match}`);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "GET") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const ownerId = parseOwnerId(req);
  if (!ownerId || !OWNER_IDS.has(ownerId)) {
    return new Response(JSON.stringify({ error: "Forbidden" }), {
      status: 403,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const url = new URL(req.url);
  const query = (url.searchParams.get("q") ?? "").trim();
  try {
    const { data: summaryRows, error: summaryError } = await supabaseAdmin.rpc("massgram_dashboard_summary");
    if (summaryError) {
      console.error("dashboard summary failed", summaryError);
      return new Response(JSON.stringify({ error: "Failed to load dashboard summary" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    let usersQuery = supabaseAdmin
      .from("massgram_clients")
      .select("user_id, username, first_name, last_name, display_name, app_version, version_code, build_channel, last_seen_at")
      .order("last_seen_at", { ascending: false })
      .limit(MAX_ROWS);

    if (query.length > 0) {
      const escaped = escapeLike(query);
      const filters = [`username.ilike.%${escaped}%`, `display_name.ilike.%${escaped}%`];
      const numericId = Number(query);
      if (Number.isFinite(numericId) && numericId > 0) {
        filters.push(`user_id.eq.${numericId}`);
      }
      usersQuery = usersQuery.or(filters.join(","));
    }

    const { data: users, error: usersError } = await usersQuery;
    if (usersError) {
      console.error("dashboard users failed", usersError);
      return new Response(JSON.stringify({ error: "Failed to load user list" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const now = Date.now();
    const rows = (users ?? []).map((row) => {
      const lastSeenAt = row.last_seen_at ?? null;
      const isOnline = !!lastSeenAt && now - Date.parse(lastSeenAt) <= 2 * 60 * 1000;
      return {
        user_id: row.user_id,
        username: row.username,
        first_name: row.first_name,
        last_name: row.last_name,
        display_name: row.display_name,
        app_version: row.app_version,
        version_code: row.version_code,
        build_channel: row.build_channel,
        last_seen_at: lastSeenAt,
        is_online: isOnline,
      };
    });

    const summary = summaryRows?.[0] ?? {
      total_users: 0,
      active_users: 0,
      offline_users: 0,
      beta_users: 0,
    };
    return new Response(JSON.stringify({ summary, users: rows }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("dashboard failed", error);
    return new Response(JSON.stringify({ error: "Unexpected dashboard error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
