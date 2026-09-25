import { handler } from "./handler.ts";

Deno.serve(handler({
  url: Deno.env.get("SUPABASE_URL") ?? "",
  serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  ownerUserId: Deno.env.get("IKI_OWNER_USER_ID") ?? "",
  origin: Deno.env.get("IKI_DASHBOARD_ORIGIN") ?? "https://iki-anti-theft.web.app",
}));
