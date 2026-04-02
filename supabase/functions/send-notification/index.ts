import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  fetchFcmTokens,
  isValidAnonAuth,
  sendFcmNotification,
} from "../_shared/notifications.ts";

type RequestBody = {
  user_id: string;
  title: string;
  body: string;
  data?: Record<string, string>;
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
    if (!body.user_id || !body.title || !body.body) {
      return new Response("Missing required fields", { status: 400 });
    }

    const tokens = await fetchFcmTokens(body.user_id);
    const result = await sendFcmNotification(tokens, {
      title: body.title,
      body: body.body,
      data: body.data ?? {},
    });

    return new Response(JSON.stringify(result), {
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
