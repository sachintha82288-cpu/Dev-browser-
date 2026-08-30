/**
 * DevBrowser Tools -- background page.
 *
 * Speaks to the native Android side over the GeckoView WebExtension port
 * ("devtools"). Every message is {id, action, ...payload} and every reply is
 * {id, ok, result|error} so the Kotlin side can await individual requests.
 */

let nativePort = null;

browser.runtime.onConnect.addListener((port) => {
  if (port.name !== "devtools") return;
  nativePort = port;
  port.onMessage.addListener(async (msg) => {
    try {
      const result = await handle(msg);
      port.postMessage({ id: msg.id, ok: true, result });
    } catch (e) {
      port.postMessage({ id: msg.id, ok: false, error: String(e && e.message ? e.message : e) });
    }
  });
  port.onDisconnect.addListener(() => { nativePort = null; });
});

async function activeTab() {
  const tabs = await browser.tabs.query({ active: true, currentWindow: true });
  if (!tabs.length) throw new Error("No active tab");
  return tabs[0];
}

async function handle(msg) {
  switch (msg.action) {
    case "ping":
      return "pong";

    /* ---------------- source code editor ---------------- */
    case "getSource": {
      const tab = await activeTab();
      const res = await browser.tabs.sendMessage(tab.id, { action: "getSource" });
      return { url: tab.url, title: tab.title, source: res.source };
    }
    case "setSource": {
      const tab = await activeTab();
      return browser.tabs.sendMessage(tab.id, { action: "setSource", source: msg.source });
    }
    case "getResource": {
      const r = await fetch(msg.url, { credentials: "include" });
      return { status: r.status, body: await r.text() };
    }

    /* ---------------- JS console ---------------- */
    case "eval": {
      const tab = await activeTab();
      return browser.tabs.sendMessage(tab.id, { action: "eval", code: msg.code });
    }
    case "injectCss": {
      const tab = await activeTab();
      await browser.tabs.insertCSS(tab.id, { code: msg.css });
      return true;
    }

    /* ---------------- cookie editor ---------------- */
    case "listCookies": {
      const tab = await activeTab();
      const url = msg.url || tab.url;
      const domain = new URL(url).hostname;
      const all = await browser.cookies.getAll({ domain });
      return all.map(serializeCookie);
    }
    case "listAllCookies": {
      const all = await browser.cookies.getAll({});
      return all.map(serializeCookie);
    }
    case "setCookie": {
      const c = msg.cookie;
      const created = await browser.cookies.set({
        url: c.url,
        name: c.name,
        value: c.value,
        domain: c.domain || undefined,
        path: c.path || "/",
        secure: !!c.secure,
        httpOnly: !!c.httpOnly,
        sameSite: c.sameSite || "no_restriction",
        expirationDate: c.expirationDate || undefined
      });
      return serializeCookie(created);
    }
    case "removeCookie": {
      await browser.cookies.remove({ url: msg.url, name: msg.name });
      return true;
    }
    case "clearCookies": {
      const all = await browser.cookies.getAll(msg.domain ? { domain: msg.domain } : {});
      for (const c of all) {
        const url = (c.secure ? "https://" : "http://") + c.domain.replace(/^\./, "") + c.path;
        await browser.cookies.remove({ url, name: c.name, storeId: c.storeId });
      }
      return all.length;
    }

    /* ---------------- storage inspector ---------------- */
    case "getStorage": {
      const tab = await activeTab();
      return browser.tabs.sendMessage(tab.id, { action: "getStorage" });
    }
    case "setStorageItem": {
      const tab = await activeTab();
      return browser.tabs.sendMessage(tab.id, {
        action: "setStorageItem", area: msg.area, key: msg.key, value: msg.value
      });
    }
    case "removeStorageItem": {
      const tab = await activeTab();
      return browser.tabs.sendMessage(tab.id, { action: "removeStorageItem", area: msg.area, key: msg.key });
    }

    default:
      throw new Error("Unknown action: " + msg.action);
  }
}

function serializeCookie(c) {
  if (!c) return null;
  return {
    name: c.name,
    value: c.value,
    domain: c.domain,
    path: c.path,
    secure: c.secure,
    httpOnly: c.httpOnly,
    sameSite: c.sameSite,
    session: c.session,
    expirationDate: c.expirationDate || 0
  };
}

/* ------------- network log, streamed to native ------------- */
browser.webRequest.onCompleted.addListener(
  (details) => {
    if (!nativePort) return;
    nativePort.postMessage({
      event: "network",
      entry: {
        url: details.url,
        method: details.method,
        status: details.statusCode,
        type: details.type,
        fromCache: details.fromCache,
        timeStamp: details.timeStamp
      }
    });
  },
  { urls: ["<all_urls>"] }
);

browser.webRequest.onErrorOccurred.addListener(
  (details) => {
    if (!nativePort) return;
    nativePort.postMessage({
      event: "network",
      entry: {
        url: details.url,
        method: details.method,
        status: -1,
        type: details.type,
        error: details.error,
        timeStamp: details.timeStamp
      }
    });
  },
  { urls: ["<all_urls>"] }
);
