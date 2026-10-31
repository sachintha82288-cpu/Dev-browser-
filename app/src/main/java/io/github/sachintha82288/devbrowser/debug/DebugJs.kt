package io.github.sachintha82288.devbrowser.debug

import org.json.JSONObject

/**
 * JavaScript injected into pages for the debugging suite.
 *
 * Two entry points matter:
 *  - [bootstrap] installs the console/error hooks once per page load.
 *  - [json]/[js] wrap a body so it can never throw across the bridge: failures
 *    come back as `{"error": "..."}` instead of a rejected GeckoResult.
 *
 * The shared helper block intentionally stays small: every panel adds its own
 * body, so the injected payload for a given panel is just helpers + that body.
 */
object DebugJs {

    /** Defensive console hook; safe to evaluate repeatedly. */
    val bootstrap: String = """
(function(){
  if (window.__db && window.__db.console) { return 'ok'; }
  var DB = window.__db = window.__db || {};
  DB.console = DB.console || [];
  DB.seq = DB.seq || 0;
  DB.groups = DB.groups || [];
  DB.max = 2500;
  DB.nodes = DB.nodes || {};
  DB.nid = DB.nid || 0;
  function str(a){
    try{
      if (a === undefined) return 'undefined';
      if (a === null) return 'null';
      if (typeof a === 'string') return a;
      if (typeof a === 'number' || typeof a === 'boolean') return String(a);
      if (a instanceof Error) return (a.stack || (a.name + ': ' + a.message));
      if (typeof a === 'function') return 'function ' + (a.name || 'anonymous') + '()';
      if (typeof Node !== 'undefined' && a instanceof Node) return '<' + (a.nodeName || 'node').toLowerCase() + '>';
      return JSON.stringify(a);
    } catch (e) { try { return String(a); } catch (e2) { return '[object]'; } }
  }
  function push(level, msg, stack){
    try {
      DB.seq++;
      DB.console.push({
        i: DB.seq, l: level, t: Date.now(),
        m: String(msg).slice(0, 8000),
        s: String(stack || '').slice(0, 6000),
        g: DB.groups.join(' > '),
        d: DB.groups.length
      });
      if (DB.console.length > DB.max) { DB.console.splice(0, DB.console.length - DB.max); }
    } catch (e) {}
  }
  var levels = ['log', 'info', 'warn', 'error', 'debug'];
  for (var i = 0; i < levels.length; i++) {
    (function(level){
      if (DB['orig_' + level]) { return; }
      var original = console[level] ? console[level].bind(console) : function(){};
      DB['orig_' + level] = original;
      console[level] = function(){
        try {
          var parts = [];
          for (var j = 0; j < arguments.length; j++) { parts.push(str(arguments[j])); }
          var stack = (level === 'error' || level === 'warn') ? (new Error().stack || '') : '';
          push(level, parts.join(' '), stack);
        } catch (e) {}
        try { original.apply(null, arguments); } catch (e) {}
      };
    })(levels[i]);
  }
  DB.groupOrig = console.group ? console.group.bind(console) : function(){};
  console.group = console.groupCollapsed = function(){
    try {
      var parts = [];
      for (var j = 0; j < arguments.length; j++) { parts.push(str(arguments[j])); }
      DB.groups.push(parts.join(' ') || 'group');
    } catch (e) {}
    try { DB.groupOrig.apply(null, arguments); } catch (e) {}
  };
  DB.groupEndOrig = console.groupEnd ? console.groupEnd.bind(console) : function(){};
  console.groupEnd = function(){
    try { DB.groups.pop(); } catch (e) {}
    try { DB.groupEndOrig(); } catch (e) {}
  };
  window.addEventListener('error', function(ev){
    try {
      var msg = (ev && ev.message) ? ev.message : 'Script error';
      var at = (ev && ev.filename) ? (ev.filename + ':' + ev.lineno + ':' + ev.colno) : '';
      var stack = (ev && ev.error && ev.error.stack) ? ev.error.stack : '';
      push('error', msg + (at ? '  (' + at + ')' : ''), stack);
      DB.lastError = { m: msg, stack: stack, at: at, t: Date.now() };
    } catch (e) {}
  }, true);
  window.addEventListener('unhandledrejection', function(ev){
    try {
      var r = ev ? ev.reason : null;
      var stack = (r && r.stack) ? r.stack : '';
      push('error', 'Unhandled promise rejection: ' + str(r), stack);
    } catch (e) {}
  });
  DB.drain = function(since){
    var out = [];
    var list = DB.console || [];
    for (var k = 0; k < list.length; k++) { if (list[k].i > since) { out.push(list[k]); } }
    return out;
  };
  DB.clear = function(){ DB.console = []; };
  DB.installed = true;
  return 'ok';
})()
""".trim()

    /** Shared helpers available to every panel script. */
    val helpers: String = """
var D = window.__db = window.__db || {};
function q(s, r){ return (r || document).querySelector(s); }
function qa(s, r){ return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
function txt(s, n){ s = String(s == null ? '' : s); return s.length > (n || 300) ? s.slice(0, n || 300) : s; }
function reg(el){
  if (!el) { return 0; }
  if (!D.nodes) { D.nodes = {}; }
  if (!D.nid) { D.nid = 0; }
  var id = ++D.nid;
  D.nodes[id] = el;
  return id;
}
function get(id){ try { return (D.nodes && D.nodes[id]) || null; } catch (e) { return null; } }
function pathOf(el){
  var parts = [];
  var n = el;
  while (n && n.parentElement) {
    parts.unshift(Array.prototype.indexOf.call(n.parentElement.children, n));
    n = n.parentElement;
  }
  return parts.join('/');
}
function resolvePath(p){
  if (!p) { return document.documentElement; }
  var n = document.documentElement;
  var parts = String(p).split('/');
  for (var i = 0; i < parts.length; i++) {
    if (!n) { return null; }
    n = n.children[parseInt(parts[i], 10)];
  }
  return n || null;
}
function sel(el){
  if (!el) { return ''; }
  if (el === document.documentElement) { return 'html'; }
  if (el === document.body) { return 'body'; }
  if (el.id) { return '#' + el.id; }
  var parts = [];
  var n = el;
  while (n && n.nodeType === 1 && n !== document.body && n !== document.documentElement) {
    var part = n.tagName.toLowerCase();
    if (n.id) { parts.unshift('#' + n.id); break; }
    var parent = n.parentElement;
    if (parent) {
      var same = 0, index = 0;
      var kids = parent.children;
      for (var i = 0; i < kids.length; i++) {
        if (kids[i].tagName === n.tagName) { same++; if (kids[i] === n) { index = same; } }
      }
      part += ':nth-of-type(' + index + ')';
    }
    parts.unshift(part);
    n = n.parentElement;
  }
  return parts.join(' > ');
}
function attrs(el){
  var out = [];
  if (!el || !el.attributes) { return out; }
  for (var i = 0; i < el.attributes.length; i++) {
    out.push([el.attributes[i].name, txt(el.attributes[i].value, 500)]);
  }
  return out;
}
function styleOf(el){ try { return window.getComputedStyle(el); } catch (e) { return null; } }
function rectOf(el){
  try {
    var r = el.getBoundingClientRect();
    return { x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height),
             px: Math.round(r.left + window.scrollX), py: Math.round(r.top + window.scrollY) };
  } catch (e) { return null; }
}
function origin(){ return location.origin; }
function absUrl(u){ try { return new URL(u, location.href).href; } catch (e) { return u; } }
""".trim()

    /** Entry used by the small inline snippets inside the panels. */
    fun json(body: String): String =
        "(function(){try{" + helpers + "\n" + body + "\n}catch(e){return JSON.stringify({error:String((e&&e.message)||e)});}})()"

    /** Wraps user-typed console/JS-panel code and reports result or stack. */
    fun evalScript(code: String): String {
        val quoted = JSONObject.quote(code)
        return "(function(){try{var __r=(0,eval)($quoted);" +
            "var __v;try{__v=(typeof __r==='string')?__r:JSON.stringify(__r);}catch(e){__v=String(__r);}" +
            "if(__v===undefined){__v='undefined';}" +
            "return JSON.stringify({ok:true,v:String(__v).slice(0,40000),e:''});}" +
            "catch(e){return JSON.stringify({ok:false,v:'',e:String((e&&(e.stack||e.message))||e).slice(0,8000)});}})()"
    }

    /** Script that drains console entries newer than [since]. */
    fun drainScript(since: Long): String =
        "(function(){try{var D=window.__db;if(!D||!D.drain){return '[]';}" +
            "return JSON.stringify(D.drain($since).slice(-500));}catch(e){return '[]';}})()"

    /** Removes all captured console entries for the current page. */
    val clearScript: String =
        "(function(){try{if(window.__db&&window.__db.clear){window.__db.clear();}return 'ok';}catch(e){return 'err';}})()"
}
