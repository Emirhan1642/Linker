import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  fetchFcmTokens,
  isValidAnonAuthHeaders,
  maskKey,
  getSupabaseAnonKey,
  sendFcmNotification,
} from "../_shared/notifications.ts";

type RequestBody = {
  recipient_id: string;
  sender_id: string;
  sender_name: string;
  message: string;
  chat_id: string;
  message_id: string;
};

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
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
        headers: { "Content-Type": "application/json" },
      });
    }

    const body = (await req.json()) as RequestBody;
    const required = [
      body.recipient_id,
      body.sender_id,
      body.sender_name,
      body.message,
      body.chat_id,
      body.message_id,
    ];
    if (required.some((value) => !value)) {
      return new Response("Missing required fields", { status: 400 });
    }

    const tokens = await fetchFcmTokens(body.recipient_id);
    const result = await sendFcmNotification(tokens, {
      title: body.sender_name,
      body: body.message,
      data: {
        type: "MESSAGE",
        title: body.sender_name,
        body: body.message,
        chatId: body.chat_id,
        messageId: body.message_id,
        senderName: body.sender_name,
        senderId: body.sender_id,
        recipientId: body.recipient_id,
      },
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
