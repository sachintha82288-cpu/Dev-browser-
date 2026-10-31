package io.github.sachintha82288.devbrowser.debug

/**
 * Sources & resources scripts (category 10).
 *
 * Page HTML is read from the live DOM (`outerHTML`), which reflects the loaded
 * document rather than the raw network response. The broken-link checker can
 * only observe same-origin status codes; cross-origin probes are reported as
 * reachable/unreachable because the browser hides their status (CORS).
 */
object SourceScripts {

    val pageSource: String = DebugJs.json(
        """
var html = '';
try { html = document.documentElement ? document.documentElement.outerHTML : ''; } catch (e) {}
return JSON.stringify({ html: html, length: html.length, url: location.href, title: document.title });
""",
    )

    val inlineScripts: String = DebugJs.json(
        """
var out = [];
try {
  var nodes = document.querySelectorAll('script:not([src])');
  for (var i = 0; i < nodes.length; i++) {
    var text = nodes[i].textContent || '';
    out.push({
      i: i,
      b: text.length,
      p: text.replace(/\s+/g, ' ').slice(0, 160)
    });
  }
} catch (e) {}
return JSON.stringify({ scripts: out });
""",
    )

    fun inlineScript(index: Int): String = DebugJs.json(
        """
try {
  var nodes = document.querySelectorAll('script:not([src])');
  var node = nodes[$index];
  return JSON.stringify({ code: node ? (node.textContent || '') : '' });
} catch (e) { return JSON.stringify({ code: '' }); }
""",
    )

    val assets: String = DebugJs.json(
        """
var out = [];
try {
  var entries = performance.getEntriesByType('resource');
  for (var i = 0; i < entries.length; i++) {
    var e = entries[i];
    var url = String(e.name || '');
    var clean = url.split('?')[0].toLowerCase();
    var kind = '';
    if (e.initiatorType === 'script' || clean.slice(-3) === '.js' || clean.slice(-4) === '.mjs') { kind = 'js'; }
    else if (e.initiatorType === 'link' || e.initiatorType === 'css' || clean.slice(-4) === '.css') { kind = 'css'; }
    if (!kind) { continue; }
    out.push({
      u: url, k: kind,
      tr: e.transferSize || 0, de: e.decodedBodySize || 0,
      d: Math.round(e.duration || 0)
    });
  }
} catch (e) {}
out.sort(function(a, b){ return b.tr - a.tr; });
return JSON.stringify({ assets: out });
""",
    )

    /** Feature 87: probe anchors and images. */
    val brokenLinksStart: String = """
(function(){
try {
  var D = window.__db = window.__db || {};
  D.linkResult = null;
  var targets = [];
  try {
    var anchors = document.querySelectorAll('a[href]');
    for (var i = 0; i < anchors.length && targets.length < 30; i++) {
      var href = anchors[i].href || '';
      if (href.indexOf('http') !== 0) { continue; }
      targets.push({ url: href, kind: 'a' });
    }
    var images = document.querySelectorAll('img[src]');
    for (var j = 0; j < images.length && targets.length < 45; j++) {
      var src = images[j].src || '';
      if (src.indexOf('http') !== 0) { continue; }
      targets.push({ url: src, kind: 'img' });
    }
  } catch (e) {}
  var results = [];
  var pending = targets.length;
  if (!pending) { D.linkResult = { links: [] }; return 'pending'; }
  function finish(){ D.linkResult = { links: results }; }
  targets.forEach(function(target){
    var sameOrigin = false;
    try { sameOrigin = new URL(target.url).origin === location.origin; } catch (e) {}
    fetch(target.url, { method: 'HEAD', mode: sameOrigin ? 'same-origin' : 'no-cors', cache: 'no-store' })
      .then(function(res){
        if (sameOrigin) {
          results.push({ u: target.url, k: target.kind, s: String(res.status) });
        } else {
          results.push({ u: target.url, k: target.kind, s: 'reachable' });
        }
        pending--;
        if (!pending) { finish(); }
      })
      .catch(function(){
        results.push({ u: target.url, k: target.kind, s: 'failed' });
        pending--;
        if (!pending) { finish(); }
      });
  });
  return 'pending';
} catch (e) { return 'err'; }
})()
""".trim()

    val asyncLinks: String =
        "(function(){try{var D=window.__db;if(!D||!D.linkResult){return 'null';}" +
            "return JSON.stringify(D.linkResult);}catch(e){return 'null';}})()"

    /** Feature 89: intrinsic vs displayed image sizes and format. */
    val images: String = DebugJs.json(
        """
var out = [];
try {
  var nodes = document.querySelectorAll('img');
  for (var i = 0; i < nodes.length && out.length < 120; i++) {
    var img = nodes[i];
    var rect = img.getBoundingClientRect();
    var url = img.currentSrc || img.src || '';
    var format = '';
    try {
      var clean = url.split('?')[0].toLowerCase();
      if (clean.slice(-4) === '.png') { format = 'png'; }
      else if (clean.slice(-4) === '.jpg' || clean.slice(-5) === '.jpeg') { format = 'jpeg'; }
      else if (clean.slice(-5) === '.webp') { format = 'webp'; }
      else if (clean.slice(-4) === '.gif') { format = 'gif'; }
      else if (clean.slice(-4) === '.svg') { format = 'svg'; }
      else if (clean.slice(-5) === '.avif') { format = 'avif'; }
      else if (clean.slice(-5) === '.ico') { format = 'ico'; }
    } catch (e) {}
    var nw = img.naturalWidth || 0;
    var nh = img.naturalHeight || 0;
    var dw = Math.round(rect.width);
    var dh = Math.round(rect.height);
    out.push({
      u: url, nw: nw, nh: nh, dw: dw, dh: dh, f: format || 'unknown',
      over: (nw > 0 && dw > 0) ? (nw > dw * 2) : false
    });
  }
} catch (e) {}
return JSON.stringify({ images: out });
""",
    )

    /** Feature 90: font families in use plus web font declarations. */
    val fonts: String = DebugJs.json(
        """
var counts = {};
try {
  var nodes = document.querySelectorAll('body *');
  for (var i = 0; i < nodes.length && i < 4000; i++) {
    var family = '';
    try { family = window.getComputedStyle(nodes[i]).fontFamily || ''; } catch (e) {}
    if (!family) { continue; }
    counts[family] = (counts[family] || 0) + 1;
  }
} catch (e) {}
var out = [];
for (var family in counts) {
  var source = 'system / fallback';
  try {
    if (document.fonts && document.fonts.forEach) {
      document.fonts.forEach(function(face){
        if (family.toLowerCase().indexOf(face.family.toLowerCase().replace(/"/g, '')) >= 0) {
          source = 'web font (' + (face.status || 'loaded') + ')';
        }
      });
    }
  } catch (e) {}
  out.push({ family: family, src: source, used: counts[family] });
}
out.sort(function(a, b){ return b.used - a.used; });
return JSON.stringify({ fonts: out.slice(0, 60) });
""",
    )

    /** Feature 88: combined size/time table for every fetched resource. */
    val allResources: String = DebugJs.json(
        """
var out = [];
try {
  var entries = performance.getEntriesByType('resource');
  for (var i = 0; i < entries.length; i++) {
    var e = entries[i];
    out.push({
      u: e.name, k: e.initiatorType || 'other',
      tr: e.transferSize || 0, de: e.decodedBodySize || 0,
      d: Math.round(e.duration || 0)
    });
  }
} catch (e) {}
out.sort(function(a, b){ return b.tr - a.tr; });
return JSON.stringify({ assets: out });
""",
    )
}
