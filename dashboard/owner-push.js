// Die native Schnittstelle ist ausschließlich für den Dashboard-Ursprung verfügbar.
export function setupOwnerPush(supabase) {
  const native = window.IkiOwner;
  const inOwnerApp = !!native || new URLSearchParams(location.search).get("owner_app") === "1";
  if (!inOwnerApp) return false;

  const panel = document.createElement("div");
  panel.style.cssText = "padding:12px;margin:12px 0;background:#eff6ff;border-radius:10px";
  const status = document.createElement("span");
  const retry = document.createElement("button");
  retry.textContent = "Benachrichtigungen verbinden";
  retry.style.marginLeft = "12px";
  panel.append(status, retry);
  document.getElementById("status").before(panel);
  if (!native) {
    status.textContent = "Native Push-Verbindung nicht verfügbar. Bitte Android System WebView aktualisieren.";
    retry.disabled = true;
    return true;
  }
  let busy = false;
  let timeout;
  // Sichtbarkeit betrifft nur die manuelle Aktion, nicht automatische Registrierungen.
  const finish = (registered = false) => {
    busy = false;
    retry.disabled = false;
    retry.hidden = registered;
    clearTimeout(timeout);
  };
  native.onmessage = async event => {
    let registered = false;
    try {
      const data = JSON.parse(event.data);
      if (data.error) throw new Error(data.error);
      if (typeof data.token !== "string") throw new Error("Kein Push-Token empfangen.");
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) throw new Error("Bitte zuerst im Dashboard anmelden.");
      const { error } = await supabase.functions.invoke("register-owner-push", { body: { token: data.token } });
      if (error) throw new Error("Verbindung fehlgeschlagen. Bitte Besitzer-Freigabe und Server-Einrichtung prüfen.");
      status.textContent = "Dieses Handy ist für Iki-Tagesberichte registriert.";
      registered = true;
    } catch (error) {
      status.textContent = error.message;
    } finally { finish(registered); }
  };
  async function register() {
    if (busy) return;
    busy = true;
    retry.disabled = true;
    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session) { status.textContent = "Für Tagesberichte bitte anmelden."; finish(); return; }
      status.textContent = "Benachrichtigungen werden verbunden…";
      timeout = setTimeout(() => { status.textContent = "Keine Antwort. Bitte erneut versuchen."; finish(); }, 20000);
      native.postMessage("getToken");
    } catch { status.textContent = "Verbindung fehlgeschlagen. Bitte erneut versuchen."; finish(); }
  }
  retry.onclick = register;
  window.addEventListener("iki-owner-resume", register);
  window.addEventListener("online", register);
  supabase.auth.onAuthStateChange(() => { setTimeout(register, 0); });
  register();
  return true;
}
