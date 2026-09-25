export function reportMessage(input: {
  android: boolean; token: string; deviceId: string; date: string; title: string; body: string; mapsUrl: string;
}) {
  if (input.android) return {
    token: input.token,
    android: { priority: "HIGH", ttl: "86400s" },
    data: { command: "DAILY_REPORT", device_id: input.deviceId, date: input.date },
  };
  return {
    token: input.token,
    notification: { title: input.title, body: input.body },
    webpush: {
      notification: { title: input.title, body: input.body, requireInteraction: true },
      fcm_options: input.mapsUrl ? { link: input.mapsUrl } : {},
    },
  };
}
