package io.github.sachintha82288.devbrowser.debug

import org.json.JSONArray

/**
 * Page-side network recorder.
 *
 * GeckoView 157 exposes no public request-observer API, so the inspector wraps
 * `fetch`, `XMLHttpRequest` and `WebSocket` and additionally reads Resource
 * Timing entries for everything else (documents, scripts, images, fonts...).
 *
 * Known limits (documented rather than hidden):
 *  - Resource-Timing entries have no status code or headers; those requests are
 *    shown with status 200 and an empty header list.
 *  - `Set-Cookie` is not readable from JS, so response cookies are only what the
 *    server exposed via the `Set-Cookie` header of a `fetch` response.
 *  - URL blocking can reject fetch/XHR/WebSocket requests; it cannot stop the
 *    parser from fetching sub-resources (that needs webRequest in an extension).
 */
object NetworkScripts {

    private const val BODY_LIMIT = 200_000

    fun recorder(blockedTargets: Set<String>, throttleProfile: String): String {
        val blockedJson = JSONArray().apply { blockedTargets.forEach { put(it) } }.toString()
        return """
(function(){
try {
  var D = window.__db = window.__db || {};
  D.blocked = $blockedJson;
  D.throttle = ${throttleLiteral(throttleProfile)};
  if (D.netInstalled) { return 'ok'; }
  D.net = D.net || [];
  D.netSeq = D.netSeq || 0;
  D.netMax = 400;
  D.ws = D.ws || [];
  var LIMIT = $BODY_LIMIT;

  function isBlocked(url){
    try {
      for (var i = 0; i < D.blocked.length; i++) {
        if (String(url).indexOf(D.blocked[i]) === 0) { return true; }
      }
    } catch (e) {}
    return false;
  }
  function push(e){
    try {
      D.netSeq++;
      e.i = D.netSeq;
      e.t = Date.now();
      D.net.push(e);
      if (D.net.length > D.netMax) { D.net.splice(0, D.net.length - D.netMax); }
    } catch (err) {}
  }
  function abs(u){ try { return new URL(u, location.href).href; } catch (e) { return String(u); } }
  function typeOf(url, hint){
    var h = String(hint || '').toLowerCase();
    if (h === 'xmlhttprequest') { return 'xhr'; }
    if (h === 'fetch') { return 'fetch'; }
    if (h === 'script') { return 'js'; }
    if (h === 'css' || h === 'link') { return 'css'; }
    if (h === 'img' || h === 'image') { return 'img'; }
    if (h === 'video' || h === 'audio' || h === 'media') { return 'media'; }
    if (h === 'font' || h === 'webfont') { return 'font'; }
    if (h === 'document' || h === 'navigation' || h === 'iframe') { return 'doc'; }
    if (h === 'websocket') { return 'ws'; }
    var u = String(url).toLowerCase().split('?')[0].split('#')[0];
    if (u.indexOf('.css') > 0) { return 'css'; }
    if (u.indexOf('.js') > 0 || u.indexOf('.mjs') > 0) { return 'js'; }
    if (/\.(png|jpe?g|gif|webp|svg|avif|ico)/.test(u)) { return 'img'; }
    if (/\.(mp4|webm|mp3|wav|ogg|m4a)/.test(u)) { return 'media'; }
    if (/\.(woff2?|ttf|otf|eot)/.test(u)) { return 'font'; }
    if (u.indexOf('.json') > 0 || u.indexOf('/api/') > 0) { return 'other'; }
    return 'other';
  }
  function headers(h){
    var out = [];
    try {
      if (!h) { return out; }
      if (typeof h.forEach === 'function' && !Array.isArray(h)) {
        h.forEach(function(v, k){ out.push([k, v]); });
      } else if (Array.isArray(h)) {
        for (var i = 0; i < h.length; i++) { out.push([String(h[i][0]), String(h[i][1])]); }
      } else {
        for (var k in h) { out.push([k, String(h[k])]); }
      }
    } catch (e) {}
    return out;
  }
  function cookies(){
    try { return document.cookie ? document.cookie.split('; ') : []; } catch (e) { return []; }
  }
  function timingFrom(e){
    try {
      return {
        dns: Math.max(0, Math.round(e.domainLookupEnd - e.domainLookupStart)),
        connect: Math.max(0, Math.round(e.connectEnd - e.connectStart)),
        tls: e.secureConnectionStart > 0 ? Math.max(0, Math.round(e.connectEnd - e.secureConnectionStart)) : 0,
        ttfb: Math.max(0, Math.round(e.responseStart - e.requestStart)),
        dl: Math.max(0, Math.round(e.responseEnd - e.responseStart))
      };
    } catch (err) { return { dns: 0, connect: 0, tls: 0, ttfb: 0, dl: 0 }; }
  }
  function throttleDelay(){
    try {
      if (D.throttle && D.throttle.latency > 0) { return D.throttle.latency; }
    } catch (e) {}
    return 0;
  }
  function offline(){
    try { return !!(D.throttle && D.throttle.offline); } catch (e) { return false; }
  }

  // ---- fetch -------------------------------------------------------
  if (window.fetch && !D.origFetch) {
    D.origFetch = window.fetch;
    window.fetch = function(input, init){
      var url = (typeof input === 'string') ? input : ((input && input.url) || '');
      var method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
      var reqHeaders = headers(init && init.headers);
      var started = performance.now();
      var finish = function(status, body, truncated, mime, respHeaders, setCookies, failed){
        var dur = Math.round(performance.now() - started);
        push({
          u: abs(url), m: method, s: status, ty: 'fetch', sz: (body || '').length, d: dur,
          rh: reqHeaders, sh: respHeaders || [], rc: cookies(), sc: setCookies || [],
          b: body || '', bt: !!truncated, mt: mime || '',
          tm: { dns: 0, connect: 0, tls: 0, ttfb: dur, dl: 0 }, bl: !!failed
        });
      };
      if (isBlocked(url)) {
        finish(0, 'Blocked by DevBrowser request blocklist', false, 'text/plain', [], [], true);
        return Promise.reject(new TypeError('Blocked by DevBrowser: ' + url));
      }
      if (offline()) {
        finish(0, 'Network throttling: offline', false, 'text/plain', [], [], true);
        return Promise.reject(new TypeError('DevBrowser emulates offline mode'));
      }
      var delay = throttleDelay();
      return D.origFetch.call(window, input, init).then(function(resp){
        var respHeaders = [];
        var setCookies = [];
        var mime = '';
        var scv = '';
        try { mime = resp.headers.get('content-type') || ''; } catch (e) {}
        try { scv = resp.headers.get('set-cookie') || ''; } catch (e) {}
        try { if (resp.headers.forEach) { resp.headers.forEach(function(v, k){ respHeaders.push([k, v]); }); } } catch (e) {}
        if (scv) { setCookies = [scv]; }
        var clone = null;
        try { clone = resp.clone(); } catch (e) {}
        if (clone) {
          clone.text().then(function(t){
            var text = String(t);
            var truncated = text.length > LIMIT;
            if (truncated) { text = text.slice(0, LIMIT); }
            if (delay > 0) { setTimeout(function(){ finish(resp.status, text, truncated, mime, respHeaders, setCookies, false); }, delay); }
            else { finish(resp.status, text, truncated, mime, respHeaders, setCookies, false); }
          }).catch(function(){ finish(resp.status, '', false, mime, respHeaders, setCookies, false); });
        } else {
          finish(resp.status, '', false, mime, respHeaders, setCookies, false);
        }
        return resp;
      }, function(err){
        finish(0, 'FAILED: ' + String(err), false, '', [], [], false);
        throw err;
      });
    };
  }

  // ---- XMLHttpRequest ---------------------------------------------
  var XHR = window.XMLHttpRequest;
  if (XHR && XHR.prototype && !D.origXhrOpen) {
    D.origXhrOpen = XHR.prototype.open;
    D.origXhrSend = XHR.prototype.send;
    D.origXhrHeader = XHR.prototype.setRequestHeader;
    XHR.prototype.open = function(method, url){
      try { this.__db = { m: method, u: url, h: [], s: performance.now() }; } catch (e) {}
      return D.origXhrOpen.apply(this, arguments);
    };
    XHR.prototype.setRequestHeader = function(name, value){
      try { if (this.__db) { this.__db.h.push([String(name), String(value)]); } } catch (e) {}
      return D.origXhrHeader.apply(this, arguments);
    };
    XHR.prototype.send = function(){
      var self = this;
      var meta = this.__db || { m: 'GET', u: '', h: [], s: performance.now() };
      if (isBlocked(meta.u)) {
        push({
          u: abs(meta.u), m: String(meta.m).toUpperCase(), s: 0, ty: 'xhr', sz: 0, d: 0,
          rh: meta.h, sh: [], rc: cookies(), sc: [], b: 'Blocked by DevBrowser', bt: false,
          mt: 'text/plain', tm: { dns: 0, connect: 0, tls: 0, ttfb: 0, dl: 0 }, bl: true
        });
        try { this.abort(); } catch (e) {}
        return;
      }
      if (offline()) {
        push({
          u: abs(meta.u), m: String(meta.m).toUpperCase(), s: 0, ty: 'xhr', sz: 0, d: 0,
          rh: meta.h, sh: [], rc: cookies(), sc: [], b: 'Network throttling: offline', bt: false,
          mt: 'text/plain', tm: { dns: 0, connect: 0, tls: 0, ttfb: 0, dl: 0 }, bl: true
        });
        try { this.abort(); } catch (e) {}
        return;
      }
      this.addEventListener('loadend', function(){
        var text = '';
        try {
          if (!self.responseType || self.responseType === 'text') { text = self.responseText || ''; }
        } catch (e) {}
        var truncated = text.length > LIMIT;
        var dur = Math.round(performance.now() - meta.s);
        push({
          u: abs(self.responseURL || meta.u), m: String(meta.m).toUpperCase(), s: self.status,
          ty: 'xhr', sz: text.length, d: dur, rh: meta.h, sh: [], rc: cookies(), sc: [],
          b: truncated ? text.slice(0, LIMIT) : text, bt: truncated, mt: '',
          tm: { dns: 0, connect: 0, tls: 0, ttfb: dur, dl: 0 }, bl: false
        });
      });
      return D.origXhrSend.apply(this, arguments);
    };
  }

  // ---- Resource timing (everything the wrappers miss) --------------
  if (window.PerformanceObserver && !D.resObserver) {
    try {
      D.resObserver = new PerformanceObserver(function(list){
        var entries = list.getEntries();
        for (var i = 0; i < entries.length; i++) {
          var e = entries[i];
          var init = String(e.initiatorType || '');
          if (init === 'xmlhttprequest' || init === 'fetch') { continue; }
          push({
            u: e.name, m: 'GET', s: 200, ty: typeOf(e.name, init),
            sz: e.transferSize || 0, d: Math.round(e.duration || 0),
            rh: [], sh: [], rc: [], sc: [], b: '', bt: false, mt: '',
            tm: timingFrom(e), bl: false
          });
        }
      });
      D.resObserver.observe({ type: 'resource', buffered: true });
    } catch (e) {}
  }

  // ---- WebSocket (feeds the network list and feature 100) ----------
  var WS = window.WebSocket;
  if (WS && !D.origWs) {
    D.origWs = WS;
    var Wrapped = new Proxy(WS, {
      construct: function(target, args){
        var socket = new target(args[0], args[1]);
        var record = { url: abs(args[0]), open: Date.now(), messages: [] };
        try { D.ws.push(record); if (D.ws.length > 40) { D.ws.shift(); } } catch (e) {}
        push({
          u: abs(args[0]), m: 'GET', s: 101, ty: 'ws', sz: 0, d: 0,
          rh: [], sh: [], rc: [], sc: [], b: '', bt: false, mt: 'websocket',
          tm: { dns: 0, connect: 0, tls: 0, ttfb: 0, dl: 0 }, bl: false
        });
        try {
          var send = socket.send.bind(socket);
          socket.send = function(data){
            try { record.messages.push({ d: 'out', m: String(data).slice(0, 4000), t: Date.now() }); } catch (e) {}
            return send(data);
          };
          socket.addEventListener('message', function(ev){
            try { record.messages.push({ d: 'in', m: String(ev.data).slice(0, 4000), t: Date.now() }); } catch (e) {}
          });
          socket.addEventListener('close', function(){
            try { record.messages.push({ d: 'sys', m: 'connection closed', t: Date.now() }); } catch (e) {}
          });
        } catch (e) {}
        return socket;
      }
    });
    try { window.WebSocket = Wrapped; } catch (e) {}
  }

  D.isBlocked = isBlocked;
  D.setBlocked = function(list){ D.blocked = list || []; };
  D.setThrottle = function(profile){ D.throttle = profile || null; };
  D.drainNet = function(since){
    var out = [];
    for (var i = 0; i < D.net.length; i++) { if (D.net[i].i > since) { out.push(D.net[i]); } }
    return out;
  };
  D.drainWs = function(){ return D.ws || []; };
  D.netInstalled = true;
  return 'ok';
} catch (e) { return 'err'; }
})()
""".trim()
    }

    fun drainScript(since: Long): String =
        "(function(){try{var D=window.__db;if(!D||!D.drainNet){return '[]';}" +
            "return JSON.stringify(D.drainNet($since).slice(-400));}catch(e){return '[]';}})()"

    val drainWsScript: String =
        "(function(){try{var D=window.__db;if(!D||!D.drainWs){return '[]';}" +
            "return JSON.stringify(D.drainWs());}catch(e){return '[]';}})()"

    fun setThrottleScript(profile: String): String =
        "(function(){try{var D=window.__db;if(D&&D.setThrottle){D.setThrottle(${throttleLiteral(profile)});}return 'ok';}" +
            "catch(e){return 'err';}})()"

    private fun throttleLiteral(profile: String): String = when (profile) {
        "slow3g" -> "{ name: 'slow-3g', latency: 2000, offline: false }"
        "fast3g" -> "{ name: 'fast-3g', latency: 900, offline: false }"
        "4g" -> "{ name: '4g', latency: 220, offline: false }"
        "offline" -> "{ name: 'offline', latency: 0, offline: true }"
        else -> "null"
    }
}
