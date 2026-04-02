import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  isValidAnonAuth,
  upsertFcmToken,
} from "../_shared/notifications.ts";

type RequestBody = {
  user_id: string;
  fcm_token: string;
  platform?: string;
};

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    const authHeader = req.headers.get("Authorization");
    if (!isValidAnonAuth(authHeader)) {
      return new Response("Unauthorized", { status: 401 });
    }

    const body = (await req.json()) as RequestBody;
    if (!body.user_id || !body.fcm_token) {
      return new Response("Missing required fields", { status: 400 });
    }

    await upsertFcmToken(body.user_id, body.fcm_token, body.platform);

    return new Response(JSON.stringify({ success: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (error) {
    return new Response(
      JSON.stringify({ success: false, message: String(error) }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }
});
