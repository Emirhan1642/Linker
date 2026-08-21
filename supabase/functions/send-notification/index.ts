import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  corsHeaders,
  fetchFcmTokens,
  isValidAnonAuthHeaders,
  sendFcmNotification,
} from "../_shared/notifications.ts";

type RequestBody = {
  user_id: string;
  title: string;
  body: string;
  data?: Record<string, string>;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405, headers: corsHeaders });
    }

    if (!isValidAnonAuthHeaders(req.headers)) {
      return new Response(
        JSON.stringify({ error: "Unauthorized (missing or invalid anon key)" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const body = (await req.json()) as RequestBody;
    if (!body.user_id || !body.title || !body.body) {
      return new Response("Missing required fields", { status: 400, headers: corsHeaders });
    }

    const tokens = await fetchFcmTokens(body.user_id);
    const result = await sendFcmNotification(tokens, {
      title: body.title,
      body: body.body,
      data: body.data ?? {},
    });

    return new Response(JSON.stringify(result), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (error) {
    return new Response(
      JSON.stringify({ success: false, message: String(error) }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
