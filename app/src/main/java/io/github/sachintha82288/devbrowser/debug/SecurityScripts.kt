package io.github.sachintha82288.devbrowser.debug

/**
 * Page scripts for the security panel (category 8).
 *
 * Header-based checks (CSP, X-Frame-Options, HSTS) are performed by re-fetching
 * the current document same-origin, because `evaluateJS` cannot see response
 * headers. Cross-origin framing of the document may hide those headers; the UI
 * says so when the fetch fails.
 */
object SecurityScripts {

    /** Collect CSP violations for as long as the page lives. */
    val installViolations: String = """
(function(){
try {
  var D = window.__db = window.__db || {};
  D.cspViolations = D.cspViolations || [];
  if (D.cspListener) { return 'ok'; }
  D.cspListener = function(ev){
    try {
      D.cspViolations.push(
        (ev.violatedDirective || 'directive') + ' blocked ' +
        (ev.blockedURI || 'resource') + ' on ' + (ev.documentURI || location.href)
      );
      if (D.cspViolations.length > 200) { D.cspViolations.shift(); }
      try { console.warn('[CSP] ' + (ev.violatedDirective || '') + ' blocked ' + (ev.blockedURI || '')); } catch (e) {}
    } catch (e) {}
  };
  document.addEventListener('securitypolicyviolation', D.cspListener, true);
  return 'ok';
} catch (e) { return 'err'; }
})()
""".trim()

    /** Synchronous page security scan (features 66, 67, 69, 70, 71, 72). */
    val scan: String = DebugJs.json(
        """
var mixed = [];
try {
  if (location.protocol === 'https:') {
    var res = performance.getEntriesByType('resource');
    for (var i = 0; i < res.length && mixed.length < 60; i++) {
      if (res[i].name.indexOf('http://') === 0) { mixed.push(res[i].name); }
    }
    var nodes = document.querySelectorAll('img[src^="http://"], script[src^="http://"], link[href^="http://"], iframe[src^="http://"]');
    for (var j = 0; j < nodes.length && mixed.length < 80; j++) {
      mixed.push(nodes[j].src || nodes[j].href || '');
    }
  }
} catch (e) {}
var unique = [];
for (var k = 0; k < mixed.length; k++) { if (unique.indexOf(mixed[k]) < 0) { unique.push(mixed[k]); } }

var cspMeta = '';
try {
  var metas = document.querySelectorAll('meta[http-equiv]');
  for (var m = 0; m < metas.length; m++) {
    var eq = String(metas[m].getAttribute('http-equiv') || '').toLowerCase();
    if (eq === 'content-security-policy') { cspMeta = metas[m].getAttribute('content') || ''; }
  }
} catch (e) {}

function classify(type, name, id, ac, ph){
  var s = (name + ' ' + id + ' ' + ac + ' ' + ph).toLowerCase();
  if (type === 'password') { return 'password'; }
  if (type === 'hidden') { return 'hidden'; }
  if (type === 'search' || s.indexOf('search') >= 0 || s.indexOf('query') >= 0) { return 'search'; }
  if (s.indexOf('card') >= 0 || s.indexOf('cc-') >= 0 || s.indexOf('cvc') >= 0 || s.indexOf('cvv') >= 0) { return 'credit-card'; }
  if (type === 'email' || s.indexOf('email') >= 0 || s.indexOf('e-mail') >= 0) { return 'email'; }
  if (s.indexOf('user') >= 0 || s.indexOf('login') >= 0 || s.indexOf('nick') >= 0) { return 'username'; }
  return 'text';
}
function preview(kind){
  if (kind === 'password') { return 'DevBrowser has no saved password for this site'; }
  if (kind === 'username') { return 'DevBrowser has no saved username for this site'; }
  if (kind === 'email') { return 'DevBrowser has no saved email for this site'; }
  if (kind === 'credit-card') { return 'DevBrowser has no saved card for this site'; }
  return '';
}

var fields = [];
try {
  var inputs = document.querySelectorAll('input, textarea, select');
  for (var f = 0; f < inputs.length && fields.length < 200; f++) {
    var el = inputs[f];
    var type = String(el.type || el.tagName || 'text').toLowerCase();
    var name = el.name || '';
    var id = el.id || '';
    var ac = el.getAttribute('autocomplete') || '';
    var ph = el.placeholder || '';
    var kind = classify(type, name, id, ac, ph);
    fields.push({
      tag: el.tagName.toLowerCase(), type: type, name: name, id: id,
      ac: ac, ph: ph, cls: kind, preview: preview(kind)
    });
  }
} catch (e) {}

var sri = [];
try {
  var assets = document.querySelectorAll('script[integrity], link[integrity]');
  for (var s = 0; s < assets.length; s++) {
    var integrity = assets[s].getAttribute('integrity') || '';
    sri.push({
      url: assets[s].src || assets[s].href || '',
      alg: integrity.split('-')[0] || '',
      expected: integrity,
      status: 'pending'
    });
  }
} catch (e) {}

var frames = 0;
try { frames = window.frames.length; } catch (e) {}

return JSON.stringify({
  origin: location.origin,
  https: location.protocol === 'https:',
  mixed: unique,
  cspMeta: cspMeta,
  violations: (window.__db && window.__db.cspViolations) || [],
  fields: fields,
  sri: sri,
  frames: frames,
  sriCoverage: (sri.length) + ' integrity attribute(s)',
  xFrameOptions: ''
});
""",
    )

    /** Fetches the document headers (CSP / XFO / HSTS) same-origin. */
    val headersStart: String = """
(function(){
try {
  var D = window.__db = window.__db || {};
  D.secHeaders = null;
  fetch(location.href, { credentials: 'same-origin', cache: 'no-store' }).then(function(res){
    var headers = {};
    res.headers.forEach(function(value, key){ headers[String(key).toLowerCase()] = value; });
    D.secHeaders = headers;
  }).catch(function(err){
    D.secHeaders = { error: String(err) };
  });
  return 'pending';
} catch (e) { return 'err'; }
})()
""".trim()

    val asyncHeaders: String =
        "(function(){try{var D=window.__db;if(!D||!D.secHeaders){return 'null';}" +
            "return JSON.stringify(D.secHeaders);}catch(e){return 'null';}})()"

    /** HSTS probe: an http:// request that silently upgrades means HSTS is on. */
    val hstsStart: String = """
(function(){
try {
  var D = window.__db = window.__db || {};
  D.hstsResult = 'unknown';
  if (location.protocol !== 'https:') { D.hstsResult = 'http-page'; return 'pending'; }
  fetch('http://' + location.host + '/', { mode: 'no-cors', cache: 'no-store', redirect: 'follow' })
    .then(function(res){
      var finalUrl = res.url || '';
      D.hstsResult = finalUrl.indexOf('https://') === 0 ? 'upgraded' : 'not-upgraded';
    })
    .catch(function(){ D.hstsResult = 'blocked'; });
  return 'pending';
} catch (e) { return 'err'; }
})()
""".trim()

    val asyncHsts: String =
        "(function(){try{var D=window.__db;return JSON.stringify({value:(D&&D.hstsResult)||'unknown'});}" +
            "catch(e){return JSON.stringify({value:'unknown'});}})()"

    /** Feature 69: recompute every integrity hash and compare. */
    val sriStart: String = """
(function(){
try {
  var D = window.__db = window.__db || {};
  var nodes = [];
  try { nodes = Array.prototype.slice.call(document.querySelectorAll('script[integrity], link[integrity]')); } catch (e) {}
  D.sriResult = { entries: [] };
  if (!nodes.length) { return 'pending'; }
  var results = [];
  var pending = nodes.length;
  function b64(buffer){
    var bytes = new Uint8Array(buffer);
    var binary = '';
    for (var i = 0; i < bytes.length; i++) { binary += String.fromCharCode(bytes[i]); }
    return btoa(binary);
  }
  function finish(){ D.sriResult = { entries: results }; }
  nodes.forEach(function(node){
    var url = node.src || node.href || '';
    var integrity = node.getAttribute('integrity') || '';
    var token = integrity.trim().split(' ')[0] || '';
    var dash = token.indexOf('-');
    var alg = dash > 0 ? token.slice(0, dash).toLowerCase() : 'sha256';
    var expected = dash > 0 ? token.slice(dash + 1) : '';
    var name = alg === 'sha384' ? 'SHA-384' : (alg === 'sha512' ? 'SHA-512' : 'SHA-256');
    var entry = { url: url, alg: alg, expected: expected, status: 'unavailable' };
    results.push(entry);
    if (!url || !expected || !window.crypto || !crypto.subtle) {
      pending--;
      if (!pending) { finish(); }
      return;
    }
    fetch(url, { cache: 'force-cache' })
      .then(function(res){ return res.arrayBuffer(); })
      .then(function(buffer){ return crypto.subtle.digest(name, buffer); })
      .then(function(digest){
        entry.status = (b64(digest) === expected) ? 'pass' : 'fail';
        pending--;
        if (!pending) { finish(); }
      })
      .catch(function(){
        entry.status = 'unavailable';
        pending--;
        if (!pending) { finish(); }
      });
  });
  return 'pending';
} catch (e) { return 'err'; }
})()
""".trim()

    val asyncSri: String =
        "(function(){try{var D=window.__db;if(!D||!D.sriResult){return 'null';}" +
            "return JSON.stringify(D.sriResult);}catch(e){return 'null';}})()"
}
