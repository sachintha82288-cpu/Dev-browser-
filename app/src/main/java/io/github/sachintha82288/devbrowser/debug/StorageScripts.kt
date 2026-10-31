package io.github.sachintha82288.devbrowser.debug

import org.json.JSONObject

/**
 * Page scripts for the storage & cookies panel (category 7).
 *
 * IndexedDB, CacheStorage and Service Worker enumeration are asynchronous, so
 * those scripts write into `window.__db.storageResult` and the panel polls
 * [asyncResult] until it is filled (GeckoView's `evaluateJS` is synchronous).
 */
object StorageScripts {

    val cookies: String = DebugJs.json(
        """
var out = [];
try {
  var parts = document.cookie ? document.cookie.split('; ') : [];
  for (var i = 0; i < parts.length; i++) {
    var part = parts[i];
    var idx = part.indexOf('=');
    out.push({
      n: idx < 0 ? part : part.slice(0, idx),
      v: idx < 0 ? '' : part.slice(idx + 1),
      d: location.hostname,
      p: '/',
      s: location.protocol === 'https:',
      h: false,
      ss: 'Lax',
      e: ''
    });
  }
} catch (e) {}
return JSON.stringify({ cookies: out });
""",
    )

    fun setCookie(name: String, value: String, days: Int): String = DebugJs.json(
        """
try {
  var expires = '';
  if ($days > 0) {
    var d = new Date();
    d.setTime(d.getTime() + ($days * 24 * 60 * 60 * 1000));
    expires = ';expires=' + d.toUTCString();
  }
  document.cookie = ${JSONObject.quote(name)} + '=' + encodeURIComponent(${JSONObject.quote(value)}) +
    expires + ';path=/;SameSite=Lax';
  return JSON.stringify({ ok: true });
} catch (e) { return JSON.stringify({ ok: false, error: String(e) }); }
""",
    )

    fun deleteCookie(name: String): String = DebugJs.json(
        """
try {
  document.cookie = ${JSONObject.quote(name)} + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
  return JSON.stringify({ ok: true });
} catch (e) { return JSON.stringify({ ok: false }); }
""",
    )

    val localDump: String = DebugJs.json(
        """
var out = [];
try {
  for (var i = 0; i < localStorage.length; i++) {
    var k = localStorage.key(i);
    var v = localStorage.getItem(k) || '';
    out.push({ k: k, v: v.length > 4000 ? v.slice(0, 4000) + '…' : v });
  }
} catch (e) {}
return JSON.stringify({ items: out });
""",
    )

    fun setLocal(key: String, value: String): String = DebugJs.json(
        """
try {
  localStorage.setItem(${JSONObject.quote(key)}, ${JSONObject.quote(value)});
  return JSON.stringify({ ok: true });
} catch (e) { return JSON.stringify({ ok: false, error: String(e) }); }
""",
    )

    fun deleteLocal(key: String): String = DebugJs.json(
        """
try { localStorage.removeItem(${JSONObject.quote(key)}); return JSON.stringify({ ok: true }); }
catch (e) { return JSON.stringify({ ok: false }); }
""",
    )

    val sessionDump: String = DebugJs.json(
        """
var out = [];
try {
  for (var i = 0; i < sessionStorage.length; i++) {
    var k = sessionStorage.key(i);
    var v = sessionStorage.getItem(k) || '';
    out.push({ k: k, v: v.length > 4000 ? v.slice(0, 4000) + '…' : v });
  }
} catch (e) {}
return JSON.stringify({ items: out });
""",
    )

    fun setSession(key: String, value: String): String = DebugJs.json(
        """
try {
  sessionStorage.setItem(${JSONObject.quote(key)}, ${JSONObject.quote(value)});
  return JSON.stringify({ ok: true });
} catch (e) { return JSON.stringify({ ok: false }); }
""",
    )

    fun deleteSession(key: String): String = DebugJs.json(
        """
try { sessionStorage.removeItem(${JSONObject.quote(key)}); return JSON.stringify({ ok: true }); }
catch (e) { return JSON.stringify({ ok: false }); }
""",
    )

    /** Feature 60: enumerate databases, stores and the first 100 records. */
    val indexedDbStart: String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
D2.storageResult = null;
if (!window.indexedDB) {
  D2.storageResult = { error: 'IndexedDB unavailable' };
  return JSON.stringify({ started: false });
}
if (!indexedDB.databases) {
  D2.storageResult = { error: 'indexedDB.databases() unsupported', databases: [] };
  return JSON.stringify({ started: false });
}
indexedDB.databases().then(function(dbs){
  var names = [];
  for (var i = 0; i < dbs.length; i++) { if (dbs[i] && dbs[i].name) { names.push(dbs[i].name); } }
  var results = [];
  var index = 0;
  function step(){
    if (index >= names.length) { D2.storageResult = { databases: results }; return; }
    var name = names[index++];
    var req = indexedDB.open(name);
    req.onerror = function(){ results.push({ name: name, stores: [], records: {} }); step(); };
    req.onblocked = function(){ results.push({ name: name, stores: [], records: {} }); step(); };
    req.onsuccess = function(){
      var db = req.result;
      var stores = [];
      for (var s = 0; s < db.objectStoreNames.length; s++) { stores.push(db.objectStoreNames[s]); }
      var entry = { name: name, stores: stores, records: {} };
      var si = 0;
      function nextStore(){
        if (si >= stores.length) { try { db.close(); } catch (e) {} results.push(entry); step(); return; }
        var storeName = stores[si++];
        var recs = [];
        try {
          var tx = db.transaction(storeName, 'readonly');
          var store = tx.objectStore(storeName);
          var cursor = store.openCursor();
          cursor.onsuccess = function(ev){
            var c = ev.target.result;
            if (c && recs.length < 100) {
              var v;
              try { v = JSON.stringify(c.value); } catch (e) { v = String(c.value); }
              recs.push({ k: String(c.key), v: String(v || '').slice(0, 2000) });
              c.continue();
            } else {
              entry.records[storeName] = recs;
              nextStore();
            }
          };
          cursor.onerror = function(){ entry.records[storeName] = []; nextStore(); };
        } catch (e) { entry.records[storeName] = []; nextStore(); }
      }
      nextStore();
    };
  }
  step();
}).catch(function(e){ D2.storageResult = { error: String(e), databases: [] }; });
return JSON.stringify({ started: true });
""",
    )

    /** Feature 61: every CacheStorage cache and its request URLs. */
    val cacheStart: String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
D2.storageResult = null;
if (!window.caches) { D2.storageResult = { caches: [], error: 'CacheStorage unavailable' }; return JSON.stringify({ started: false }); }
caches.keys().then(function(keys){
  var out = [];
  var index = 0;
  function step(){
    if (index >= keys.length) { D2.storageResult = { caches: out }; return; }
    var name = keys[index++];
    caches.open(name).then(function(cache){
      cache.keys().then(function(reqs){
        var urls = [];
        for (var i = 0; i < reqs.length; i++) { urls.push(reqs[i].url); }
        out.push({ name: name, urls: urls });
        step();
      }).catch(function(){ out.push({ name: name, urls: [] }); step(); });
    }).catch(function(){ out.push({ name: name, urls: [] }); step(); });
  }
  step();
}).catch(function(e){ D2.storageResult = { caches: [], error: String(e) }; });
return JSON.stringify({ started: true });
""",
    )

    /** Feature 62: registered service workers. */
    val workerStart: String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
D2.storageResult = null;
if (!navigator.serviceWorker || !navigator.serviceWorker.getRegistrations) {
  D2.storageResult = { workers: [], error: 'Service workers unavailable (needs a secure context)' };
  return JSON.stringify({ started: false });
}
navigator.serviceWorker.getRegistrations().then(function(regs){
  var out = [];
  for (var i = 0; i < regs.length; i++) {
    var r = regs[i];
    var w = r.active || r.installing || r.waiting;
    out.push({
      scope: r.scope,
      script: w ? (w.scriptURL || '') : '',
      state: w ? (w.state || 'unknown') : 'none'
    });
  }
  D2.storageResult = { workers: out };
}).catch(function(e){ D2.storageResult = { workers: [], error: String(e) }; });
return JSON.stringify({ started: true });
""",
    )

    val asyncResult: String =
        "(function(){try{var D=window.__db;if(!D||!D.storageResult){return 'null';}" +
            "return JSON.stringify(D.storageResult);}catch(e){return 'null';}})()"

    /** Feature 63: clear every storage area for the current origin. */
    val clearSiteData: String = DebugJs.json(
        """
var done = [];
try { localStorage.clear(); done.push('localStorage'); } catch (e) {}
try { sessionStorage.clear(); done.push('sessionStorage'); } catch (e) {}
try {
  var parts = document.cookie ? document.cookie.split('; ') : [];
  for (var i = 0; i < parts.length; i++) {
    var name = parts[i].split('=')[0];
    document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/';
  }
  done.push('cookies');
} catch (e) {}
try {
  if (window.indexedDB && indexedDB.databases) {
    indexedDB.databases().then(function(dbs){
      for (var i = 0; i < dbs.length; i++) { indexedDB.deleteDatabase(dbs[i].name); }
    });
    done.push('indexedDB');
  }
} catch (e) {}
try {
  if (window.caches) {
    caches.keys().then(function(keys){
      for (var i = 0; i < keys.length; i++) { caches.delete(keys[i]); }
    });
    done.push('cacheStorage');
  }
} catch (e) {}
return JSON.stringify({ cleared: done });
""",
    )
}
