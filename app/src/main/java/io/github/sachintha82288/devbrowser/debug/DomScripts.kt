package io.github.sachintha82288.devbrowser.debug

import org.json.JSONObject

/**
 * Page scripts for the DOM inspector and CSS panel.
 *
 * Elements are addressed by their *path* (child indices from `<html>`), which
 * survives page mutations much better than cached node ids and keeps the
 * Kotlin-side model simple.
 */
object DomScripts {

    /** Serializes the element tree; [maxNodes] bounds the payload size. */
    fun tree(maxNodes: Int): String = DebugJs.json(
        """
D.nodes = {};
D.nid = 0;
var limit = $maxNodes;
var count = 0;
function ser(el, depth){
  if (!el || el.nodeType !== 1 || count >= limit || depth > 40) { return null; }
  count++;
  var kids = [];
  var n = Math.min(el.children.length, 80);
  for (var i = 0; i < n; i++) { var k = ser(el.children[i], depth + 1); if (k) { kids.push(k); } }
  var cls = '';
  try { cls = (typeof el.className === 'string') ? el.className : (el.getAttribute('class') || ''); } catch (e) {}
  return {
    i: count, p: pathOf(el), t: el.tagName.toLowerCase(), id: el.id || '',
    c: txt(cls, 120), x: txt(el.children.length ? '' : (el.textContent || ''), 80),
    n: el.children.length, k: kids
  };
}
var root = ser(document.documentElement, 0);
return JSON.stringify({ root: root, count: count, title: document.title, url: location.href });
""",
    )

    private fun quoted(path: String): String = JSONObject.quote(path)

    /** Full description of one element (feature 25-30 need all of this). */
    fun describe(path: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ error: 'missing' }); }
return JSON.stringify({
  p: pathOf(el),
  t: el.tagName.toLowerCase(),
  s: sel(el),
  h: txt(el.outerHTML || '', 6000),
  a: attrs(el),
  x: txt(el.textContent || '', 6000),
  r: rectOf(el)
});
""",
    )

    fun setAttribute(path: String, name: String, value: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ ok: false }); }
el.setAttribute(${JSONObject.quote(name)}, ${JSONObject.quote(value)});
return JSON.stringify({ ok: true });
""",
    )

    fun setText(path: String, text: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ ok: false }); }
el.textContent = ${JSONObject.quote(text)};
return JSON.stringify({ ok: true });
""",
    )

    /** Feature 28/36: unique-ish CSS selector, id/attribute first. */
    fun selector(path: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ s: '' }); }
var out = '';
if (el.id) {
  out = '#' + el.id;
} else {
  var attrs = el.attributes || [];
  for (var i = 0; i < attrs.length; i++) {
    var a = attrs[i];
    if (a.name.indexOf('data-') === 0 && a.value && a.value.length < 40) {
      out = el.tagName.toLowerCase() + '[' + a.name + '="' + a.value + '"]';
      break;
    }
  }
  if (!out) { out = sel(el); }
}
return JSON.stringify({ s: out });
""",
    )

    fun outerHtml(path: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
return JSON.stringify({ h: el ? (el.outerHTML || '') : '' });
""",
    )

    /** Feature 27: neon rectangle drawn over the real page. */
    fun highlight(path: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ ok: false }); }
var r = el.getBoundingClientRect();
var box = document.getElementById('__db_highlight');
if (!box) {
  box = document.createElement('div');
  box.id = '__db_highlight';
  box.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;' +
    'border:2px solid #FF2E63;box-shadow:0 0 14px #FF2E63;background:rgba(255,46,99,0.10);';
  (document.body || document.documentElement).appendChild(box);
}
box.style.left = Math.max(0, r.left - 1) + 'px';
box.style.top = Math.max(0, r.top - 1) + 'px';
box.style.width = Math.max(1, r.width) + 'px';
box.style.height = Math.max(1, r.height) + 'px';
box.style.display = 'block';
return JSON.stringify({ ok: true, w: Math.round(r.width), h: Math.round(r.height) });
""",
    )

    val clearHighlight: String = DebugJs.json(
        """
var box = document.getElementById('__db_highlight');
if (box) { box.style.display = 'none'; }
return JSON.stringify({ ok: true });
""",
    )

    /** Feature 24: tap-to-inspect picker. */
    fun picker(enabled: Boolean): String = DebugJs.json(
        """
if (!D.pickHandler) {
  D.pickHandler = function(ev){
    try {
      var el = ev.target;
      ev.preventDefault();
      ev.stopPropagation();
      D.picked = { p: pathOf(el), s: sel(el), t: (el.tagName || '').toLowerCase() };
      var r = el.getBoundingClientRect();
      var box = document.getElementById('__db_highlight');
      if (!box) {
        box = document.createElement('div');
        box.id = '__db_highlight';
        box.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;' +
          'border:2px solid #00E5FF;background:rgba(0,229,255,0.12);';
        (document.body || document.documentElement).appendChild(box);
      }
      box.style.left = r.left + 'px';
      box.style.top = r.top + 'px';
      box.style.width = r.width + 'px';
      box.style.height = r.height + 'px';
      box.style.display = 'block';
    } catch (e) {}
  };
}
if (${if (enabled) "true" else "false"}) {
  document.addEventListener('click', D.pickHandler, true);
  D.picking = true;
} else {
  document.removeEventListener('click', D.pickHandler, true);
  D.picking = false;
}
return JSON.stringify({ picking: !!D.picking });
""",
    )

    /** Returns the last picked element, or `null`. */
    val picked: String =
        "(function(){try{var D=window.__db;if(!D||!D.picked){return 'null';}" +
            "var p=D.picked;D.picked=null;return JSON.stringify(p);}catch(e){return 'null';}})()"

    /** Feature 30: every computed property, alphabetical. */
    fun computedStyles(path: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ error: 'missing' }); }
var cs = styleOf(el);
if (!cs) { return JSON.stringify({ error: 'no style' }); }
var out = [];
for (var i = 0; i < cs.length; i++) {
  var name = cs[i];
  out.push([name, cs.getPropertyValue(name)]);
}
out.sort(function(a, b){ return a[0] < b[0] ? -1 : (a[0] > b[0] ? 1 : 0); });
var box = {
  margin: [cs.marginTop, cs.marginRight, cs.marginBottom, cs.marginLeft],
  border: [cs.borderTopWidth, cs.borderRightWidth, cs.borderBottomWidth, cs.borderLeftWidth],
  padding: [cs.paddingTop, cs.paddingRight, cs.paddingBottom, cs.paddingLeft],
  width: cs.width, height: cs.height
};
return JSON.stringify({ props: out, box: box, selector: sel(el) });
""",
    )

    /** Feature 31: every stylesheet rule that matches the element. */
    fun matchingRules(path: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ rules: [] }); }
var out = [];
var sheets = document.styleSheets || [];
for (var s = 0; s < sheets.length; s++) {
  var sheet = sheets[s];
  var rules = null;
  try { rules = sheet.cssRules; } catch (e) { rules = null; }
  if (!rules) { continue; }
  var source = sheet.href || 'inline style';
  for (var i = 0; i < rules.length; i++) {
    var rule = rules[i];
    if (!rule.selectorText) { continue; }
    var matched = false;
    try {
      var parts = rule.selectorText.split(',');
      for (var p = 0; p < parts.length; p++) {
        if (el.matches && el.matches(parts[p].trim())) { matched = true; break; }
      }
    } catch (e) {}
    if (!matched) { continue; }
    var decls = [];
    var st = rule.style;
    if (st) {
      for (var d = 0; d < st.length; d++) {
        var prop = st[d];
        decls.push([prop, st.getPropertyValue(prop)]);
      }
    }
    out.push({ sel: rule.selectorText, src: source, origin: (rule.parentRule ? 'nested' : 'author'), d: decls });
  }
}
var inline = el.getAttribute('style');
if (inline) {
  var st2 = el.style;
  var dl = [];
  for (var k = 0; k < st2.length; k++) { dl.push([st2[k], st2.getPropertyValue(st2[k])]); }
  out.push({ sel: 'element.style', src: 'inline', origin: 'inline', d: dl });
}
return JSON.stringify({ rules: out });
""",
    )

    /** Feature 32: live CSS edits through the inline style (highest priority). */
    fun applyProperty(path: String, property: String, value: String): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ ok: false }); }
try {
  if (!value) { el.style.removeProperty(${JSONObject.quote(property)}); }
  else { el.style.setProperty(${JSONObject.quote(property)}, ${JSONObject.quote(value)}, 'important'); }
  return JSON.stringify({ ok: true });
} catch (e) { return JSON.stringify({ ok: false }); }
""",
    )

    /**
     * Features 34/35: force a pseudo-class by cloning matching rules without the
     * pseudo and adding a marker class to the element. GeckoView has no devtools
     * "force state" API, so this is the closest equivalent.
     */
    fun forceState(path: String, pseudo: String, enabled: Boolean): String = DebugJs.json(
        """
var el = resolvePath(${quoted(path)});
if (!el) { return JSON.stringify({ ok: false }); }
var cls = '__db_forced';
try {
  if (${if (enabled) "false" else "true"}) {
    el.classList.remove(cls);
    var old = document.getElementById('__db_force_style');
    if (old) { old.parentNode.removeChild(old); }
    return JSON.stringify({ ok: true });
  }
  el.classList.add(cls);
  var css = [];
  var sheets = document.styleSheets || [];
  for (var s = 0; s < sheets.length; s++) {
    var rules = null;
    try { rules = sheets[s].cssRules; } catch (e) { rules = null; }
    if (!rules) { continue; }
    for (var i = 0; i < rules.length; i++) {
      var rule = rules[i];
      if (!rule.selectorText || rule.selectorText.indexOf(${JSONObject.quote(pseudo)}) < 0) { continue; }
      var parts = rule.selectorText.split(',');
      for (var p = 0; p < parts.length; p++) {
        var part = parts[p].trim();
        if (part.indexOf(${JSONObject.quote(pseudo)}) < 0) { continue; }
        var base = part.split(${JSONObject.quote(pseudo)}).join('.' + cls);
        css.push(base + '{' + rule.style.cssText + '}');
      }
    }
  }
  var tag = document.getElementById('__db_force_style');
  if (!tag) {
    tag = document.createElement('style');
    tag.id = '__db_force_style';
    (document.head || document.documentElement).appendChild(tag);
  }
  tag.textContent = css.join('\n');
  return JSON.stringify({ ok: true, rules: css.length });
} catch (e) { return JSON.stringify({ ok: false, error: String(e) }); }
""",
    )
}
