import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  corsHeaders,
  isValidAnonAuthHeaders,
  maskKey,
  getSupabaseAnonKey,
  upsertFcmToken,
} from "../_shared/notifications.ts";

type RequestBody = {
  user_id: string;
  fcm_token: string;
  platform?: string;
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
      const authHeader = req.headers.get("Authorization");
      const apiKey = req.headers.get("apikey");
      const envKey = getSupabaseAnonKey();
      const debug = {
        error: "Unauthorized (missing or invalid anon key)",
        authorization: maskKey(authHeader),
        apikey: maskKey(apiKey),
        env: maskKey(envKey),
      };
      return new Response(JSON.stringify(debug), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const body = (await req.json()) as RequestBody;
    const userId = body.user_id?.trim();
    const fcmToken = body.fcm_token?.trim();
    const platform = body.platform?.trim();

    if (!userId || !fcmToken || fcmToken.length < 10) {
      return new Response(JSON.stringify({ error: "Missing or invalid required fields (user_id, fcm_token)" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    await upsertFcmToken(userId, fcmToken, platform);

    return new Response(JSON.stringify({ success: true }), {
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
