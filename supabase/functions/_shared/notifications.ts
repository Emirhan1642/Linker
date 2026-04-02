import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

type TokenRow = {
  fcm_token: string;
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
  return getEnvOrThrow("SUPABASE_ANON_KEY");
}

export function isValidAnonAuth(authHeader: string | null): boolean {
  if (!authHeader) return false;
  return authHeader.trim() === `Bearer ${getSupabaseAnonKey()}`;
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

  const fcmKey = getEnvOrThrow("FCM_SERVER_KEY");
  const response = await fetch("https://fcm.googleapis.com/fcm/send", {
    method: "POST",
    headers: {
      Authorization: `key=${fcmKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      registration_ids: tokens,
      notification: {
        title: payload.title,
        body: payload.body,
      },
      data: payload.data ?? {},
    }),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`FCM error: ${response.status} ${text}`);
  }

  const json = await response.json();
  return { success: true, message: "Notification sent", message_id: json?.multicast_id?.toString?.() };
}
