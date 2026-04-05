import { corsHeaders } from "./_shared/cors.ts";
import { supabaseAdmin } from "./_shared/supabase-admin.ts";

const OWNER_IDS = new Set(["6539627752"]);
const MAX_ROWS = 100;

function parseOwnerId(req: Request): string | null {
  const url = new URL(req.url);
  return req.headers.get("x-massgram-owner-id") ?? url.searchParams.get("owner_id");
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
  const fingerprint = (url.searchParams.get("fingerprint") ?? "").trim();

  try {
    if (fingerprint) {
      const { data: summaryRows, error: summaryError } = await supabaseAdmin.rpc("massgram_issue_detail_summary", {
        target_fingerprint: fingerprint,
      });
      if (summaryError) {
        console.error("issue detail summary failed", summaryError);
        return new Response(JSON.stringify({ error: "Failed to load issue detail" }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
      const summary = summaryRows?.[0] ?? null;
      if (!summary) {
        return new Response(JSON.stringify({ error: "Issue not found" }), {
          status: 404,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
      const { data: userRows, error: userError } = await supabaseAdmin.rpc("massgram_issue_detail_events", {
        target_fingerprint: fingerprint,
        max_rows: 20,
      });
      if (userError) {
        console.error("issue detail events failed", userError);
        return new Response(JSON.stringify({ error: "Failed to load affected users" }), {
          status: 500,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({
        detail: {
          ...summary,
          users: userRows ?? [],
        },
      }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const { data: summaryRows, error: summaryError } = await supabaseAdmin.rpc("massgram_issue_summary");
    if (summaryError) {
      console.error("issue summary failed", summaryError);
      return new Response(JSON.stringify({ error: "Failed to load issues summary" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
    const { data: issueRows, error: listError } = await supabaseAdmin.rpc("massgram_issue_list", {
      search_query: query,
      max_rows: MAX_ROWS,
    });
    if (listError) {
      console.error("issue list failed", listError);
      return new Response(JSON.stringify({ error: "Failed to load issues list" }), {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({
      summary: summaryRows?.[0] ?? {
        crash_users_24h: 0,
        new_issues_24h: 0,
        top_fingerprint: null,
        top_title: null,
        top_count: 0,
      },
      issues: issueRows ?? [],
    }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("owner issues failed", error);
    return new Response(JSON.stringify({ error: "Unexpected owner issues error" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
