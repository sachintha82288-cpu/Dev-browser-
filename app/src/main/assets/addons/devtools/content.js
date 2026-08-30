/** DevBrowser Tools -- content script running in the page. */

browser.runtime.onMessage.addListener((msg) => {
  switch (msg.action) {
    case "getSource":
      return Promise.resolve({ source: document.documentElement.outerHTML });

    case "setSource": {
      // Rewrite the live DOM with the edited markup without navigating away.
      document.open();
      document.write(msg.source);
      document.close();
      return Promise.resolve(true);
    }

    case "eval": {
      try {
        const script = document.createElement("script");
        const token = "__devbrowser_" + Math.random().toString(36).slice(2);
        script.textContent =
          "try{window['" + token + "']=String(eval(" + JSON.stringify(msg.code) + "));}" +
          "catch(e){window['" + token + "']='Error: '+e.message;}";
        document.documentElement.appendChild(script);
        const out = window.wrappedJSObject
          ? window.wrappedJSObject[token]
          : window[token];
        script.remove();
        return Promise.resolve({ result: String(out) });
      } catch (e) {
        return Promise.resolve({ result: "Error: " + e.message });
      }
    }

    case "getStorage": {
      const dump = (store) => {
        const out = [];
        try {
          for (let i = 0; i < store.length; i++) {
            const k = store.key(i);
            out.push({ key: k, value: store.getItem(k) });
          }
        } catch (e) { /* storage blocked */ }
        return out;
      };
      return Promise.resolve({
        local: dump(window.localStorage),
        session: dump(window.sessionStorage)
      });
    }

    case "setStorageItem": {
      const store = msg.area === "session" ? window.sessionStorage : window.localStorage;
      store.setItem(msg.key, msg.value);
      return Promise.resolve(true);
    }

    case "removeStorageItem": {
      const store = msg.area === "session" ? window.sessionStorage : window.localStorage;
      store.removeItem(msg.key);
      return Promise.resolve(true);
    }
  }
  return false;
});
