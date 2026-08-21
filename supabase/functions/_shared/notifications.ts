import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

type TokenRow = {
  fcm_token: string;
};

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function getEnvOrThrow(key: string): string {
  const value = Deno.env.get(key);
  if (!value) {
    throw new Error(`Missing environment variable: ${key}`);
  }
  return value;
}

export function getSupabaseAdminClient() {
  const supabaseUrl = getEnvOrThrow("SUPABASE_URL");
  const serviceRoleKey = getEnvOrThrow("SUPABASE_SERVICE_ROLE_KEY");
  return createClient(supabaseUrl, serviceRoleKey);
}

export function getSupabaseAnonKey() {
  // Try custom key first, fallback to default SUPABASE_ANON_KEY
  const customKey = Deno.env.get("LINKER_ANON_KEY");
  if (customKey) {
    let value = customKey.trim();
    if (value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length - 1).trim();
    }
    if (value.startsWith("'") && value.endsWith("'")) {
      value = value.substring(1, value.length - 1).trim();
    }
    return value;
  }
  
  let value = getEnvOrThrow("SUPABASE_ANON_KEY").trim();
  if (value.startsWith("\"") && value.endsWith("\"")) {
    value = value.substring(1, value.length - 1).trim();
  }
  if (value.startsWith("'") && value.endsWith("'")) {
    value = value.substring(1, value.length - 1).trim();
  }
  return value;
}

export function isValidAnonAuthHeader(authHeader: string | null): boolean {
  if (!authHeader) return false;
  let trimmed = authHeader.trim();
  if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
    trimmed = trimmed.substring(1, trimmed.length - 1).trim();
  }
  if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
    trimmed = trimmed.substring(1, trimmed.length - 1).trim();
  }
  if (trimmed.startsWith("Bearer ")) {
    const token = trimmed.substring("Bearer ".length).trim();
    return token === getSupabaseAnonKey();
  }
  return trimmed === getSupabaseAnonKey();
}

export function isValidAnonAuthHeaders(headers: Headers): boolean {
  const authHeader = headers.get("Authorization");
  const apiKey = headers.get("apikey");
  return isValidAnonAuthHeader(authHeader) || isValidAnonAuthHeader(apiKey);
}

export function maskKey(value: string | null) {
  if (!value) return { len: 0, mask: "none" };
  const trimmed = value.trim();
  if (trimmed.length < 8) return { len: trimmed.length, mask: trimmed };
  return { len: trimmed.length, mask: `${trimmed.slice(0, 4)}...${trimmed.slice(-4)}` };
}

export async function fetchFcmTokens(userId: string): Promise<string[]> {
  const supabase = getSupabaseAdminClient();
  const { data, error } = await supabase
    .from("user_push_tokens")
    .select("fcm_token")
    .eq("user_id", userId);

  if (error) {
    throw new Error(`Failed to fetch tokens: ${error.message}`);
  }

  return (data as TokenRow[]).map((row) => row.fcm_token).filter(Boolean);
}

export async function upsertFcmToken(userId: string, token: string, platform?: string) {
  const supabase = getSupabaseAdminClient();
  const { error } = await supabase
    .from("user_push_tokens")
    .upsert(
      [
        {
          user_id: userId,
          fcm_token: token,
          platform: platform ?? null,
          updated_at: new Date().toISOString(),
        },
      ],
      { onConflict: "user_id,fcm_token" },
    );

  if (error) {
    throw new Error(`Failed to upsert token: ${error.message}`);
  }
}

type FcmPayload = {
  title: string;
  body: string;
  data?: Record<string, string>;
};

export async function sendFcmNotification(tokens: string[], payload: FcmPayload) {
  if (tokens.length === 0) {
    return { success: true, message: "No tokens for recipient" };
  }

  const serviceAccountJson = getEnvOrThrow("FCM_SERVICE_ACCOUNT");
  const serviceAccount = JSON.parse(serviceAccountJson) as {
    client_email: string;
    private_key: string;
    project_id: string;
  };
  const projectId = Deno.env.get("FCM_PROJECT_ID")?.trim() || serviceAccount.project_id;
  if (!projectId) {
    throw new Error("Missing FCM project id");
  }

  const accessToken = await getAccessToken(serviceAccount);
  let successCount = 0;
  let lastMessageId: string | null = null;

  for (const token of tokens) {
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          android: { priority: "high" },
          data: payload.data ?? {},
        },
      }),
    });

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`FCM error: ${response.status} ${text}`);
    }

    const json = await response.json();
    successCount += 1;
    lastMessageId = json?.name ?? lastMessageId;
  }

  return {
    success: true,
    message: `Notification sent (${successCount})`,
    message_id: lastMessageId ?? undefined,
  };
}

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
    ["sign"],
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
