import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {
  fetchFcmTokens,
  isValidAnonAuthHeaders,
  getSupabaseAnonKey,
  maskKey,
} from "../_shared/notifications.ts";

type RequestBody = {
  recipient_id: string;
  message_id: string;
  chat_id?: string;
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
    
    if (!body.recipient_id || !body.message_id) {
      return new Response("Missing required fields", { status: 400 });
    }

    const tokens = await fetchFcmTokens(body.recipient_id);
    
    if (tokens.length === 0) {
      return new Response(
        JSON.stringify({ success: true, message: "No tokens for recipient" }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }

    const serviceAccountJson = Deno.env.get("FCM_SERVICE_ACCOUNT");
    if (!serviceAccountJson) {
      throw new Error("Missing FCM_SERVICE_ACCOUNT");
    }
    
    const serviceAccount = JSON.parse(serviceAccountJson) as {
      client_email: string;
      private_key: string;
      project_id: string;
    };
    const projectId = Deno.env.get("FCM_PROJECT_ID")?.trim() || serviceAccount.project_id;
    if (!projectId) {
      throw new Error("Missing FCM project id");
    }

    // Send data-only message to trigger notification dismissal on Android
    const accessToken = await getAccessToken(serviceAccount);
    let successCount = 0;

    for (const token of tokens) {
      const dataPayload: Record<string, string> = {
        type: "DELETE_NOTIFICATION",
        messageId: body.message_id,
      };
      
      // Add chatId if provided
      if (body.chat_id) {
        dataPayload.chatId = body.chat_id;
      }
      
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              android: { priority: "high" },
              data: dataPayload,
            },
          }),
        }
      );

      if (response.ok) {
        successCount += 1;
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: `Delete notification sent (${successCount})`,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  } catch (error) {
    return new Response(
      JSON.stringify({ success: false, message: String(error) }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});

async function getAccessToken(serviceAccount: {
  client_email: string;
  private_key: string;
}) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const signingInput = `${encodedHeader}.${encodedPayload}`;

  const signature = await signJwt(signingInput, serviceAccount.private_key);
  const jwt = `${signingInput}.${signature}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`OAuth error: ${response.status} ${text}`);
  }

  const json = await response.json();
  return json.access_token as string;
}

async function signJwt(input: string, privateKeyPem: string) {
  const key = await importPrivateKey(privateKeyPem);
  const data = new TextEncoder().encode(input);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, data);
  return base64UrlEncodeBytes(new Uint8Array(signature));
}

async function importPrivateKey(pem: string) {
  const pkcs8 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const binary = atob(pkcs8);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return crypto.subtle.importKey(
    "pkcs8",
    bytes.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

function base64UrlEncode(input: string) {
  const bytes = new TextEncoder().encode(input);
  return base64UrlEncodeBytes(bytes);
}

function base64UrlEncodeBytes(bytes: Uint8Array) {
  let binary = "";
  for (const b of bytes) {
    binary += String.fromCharCode(b);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
