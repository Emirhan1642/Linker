import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  corsHeaders,
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
  /** PRIVATE | GROUP | REACTION — Android tarafında bildirim dalı ve kanal için */
  chat_type?: string;
  /** Grup sohbetleri için grup adı */
  chat_name?: string;
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
    
    // For reactions, chat_id is optional
    const isReaction = body.chat_type === "REACTION";
    const required = [
      body.recipient_id,
      body.sender_id,
      body.sender_name,
      body.message,
      body.message_id,
    ];
    if (!isReaction) {
      required.push(body.chat_id);
    }
    
    if (required.some((value) => !value)) {
      return new Response("Missing required fields", { status: 400, headers: corsHeaders });
    }

    const tokens = await fetchFcmTokens(body.recipient_id);
    const chatType = body.chat_type?.trim() || "PRIVATE";

    // For reactions, use LIKE type instead of MESSAGE
    const notificationType = isReaction ? "LIKE" : "MESSAGE";

    const result = await sendFcmNotification(tokens, {
      title: body.sender_name,
      body: body.message,
      data: {
        type: notificationType,
        title: body.sender_name,
        body: body.message,
        chatId: body.chat_id || "",
        messageId: body.message_id,
        senderName: body.sender_name,
        senderId: body.sender_id,
        recipientId: body.recipient_id,
        chatType,
        chatName: body.chat_name || "",
      },
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
