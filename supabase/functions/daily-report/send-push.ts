import { reportMessage } from "./push-message.ts";

type Report = { deviceId: string; date: string; title: string; body: string; mapsUrl: string };
type Tokens = { fcm_token?: string | null; android_fcm_token?: string | null };

// Ein Sender pro Berichtslauf; nur erfolgreiche OAuth-Ergebnisse werden wiederverwendet.
export function createReportPushSender(options: {
  projectId: string;
  getAccessToken: () => Promise<string>;
  send?: typeof fetch;
  log?: (message: string) => void;
}) {
  const send = options.send ?? fetch;
  const log = options.log ?? ((message: string) => console.log(message));
  let accessToken: string | null = null;
  async function channel(android: boolean, token: string | null | undefined, report: Report) {
    if (!token) return false;
    const name = android ? "Android" : "Web";
    if (!options.projectId) { log(`${name}-Push: Firebase-Projekt fehlt.`); return false; }
    try {
      if (!accessToken) accessToken = await options.getAccessToken();
      if (!accessToken) throw new Error("Missing access token");
    } catch {
      // Auch Parser-/Netzwerkfehler können vertrauliche Inhalte enthalten.
      log(`${name}-Push: OAuth-Zugriff fehlgeschlagen.`);
      return false;
    }
    try {
      const response = await send(
        `https://fcm.googleapis.com/v1/projects/${options.projectId}/messages:send`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
          body: JSON.stringify({ message: reportMessage({ ...report, android, token }) }),
          signal: AbortSignal.timeout(15000),
        },
      );
      // Antwortkörper weder lesen noch loggen: nur numerischen HTTP-Status verwenden.
      if (response.body) await response.body.cancel();
      if (!response.ok) {
        if (response.status === 401) accessToken = null;
        log(`${name}-Push: FCM HTTP ${response.status}.`);
        return false;
      }
      log(`${name}-Push: von FCM angenommen.`);
      return true;
    } catch {
      log(`${name}-Push: Versand fehlgeschlagen.`);
      return false;
    }
  }
  return async (tokens: Tokens, report: Report) => {
    // Jeder Kanal hat eigene Fehlerbehandlung. Android ersetzt niemals Web.
    const web = await channel(false, tokens.fcm_token, report);
    const android = await channel(true, tokens.android_fcm_token, report);
    return { web, android };
  };
}
