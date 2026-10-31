package io.github.sachintha82288.devbrowser.debug

/**
 * Accessibility & advanced scripts (category 12).
 *
 * The accessibility tree is reconstructed from ARIA attributes and semantic
 * elements; GeckoView does not expose the platform accessibility tree, so roles
 * and names are derived with the same heuristics screen readers use.
 */
object AccessibilityScripts {

    private const val CLEAR_LAYER = """
function __dbClearLayer(id){
  var old = document.getElementById(id);
  if (old && old.parentNode) { old.parentNode.removeChild(old); }
}
"""

    /** Feature 97: role + name + value tree. */
    val ariaTree: String = DebugJs.json(
        """
function roleOf(el){
  var role = el.getAttribute && el.getAttribute('role');
  if (role) { return role; }
  var tag = (el.tagName || '').toLowerCase();
  if (tag === 'a') { return el.hasAttribute('href') ? 'link' : 'generic'; }
  if (tag === 'button') { return 'button'; }
  if (tag === 'input') {
    var type = (el.type || 'text').toLowerCase();
    if (type === 'checkbox') { return 'checkbox'; }
    if (type === 'radio') { return 'radio'; }
    if (type === 'range') { return 'slider'; }
    if (type === 'submit' || type === 'button') { return 'button'; }
    if (type === 'search') { return 'searchbox'; }
    return 'textbox';
  }
  if (tag === 'select') { return 'combobox'; }
  if (tag === 'textarea') { return 'textbox'; }
  if (tag === 'img') { return 'img'; }
  if (tag === 'h1' || tag === 'h2' || tag === 'h3' || tag === 'h4' || tag === 'h5' || tag === 'h6') { return 'heading'; }
  if (tag === 'nav') { return 'navigation'; }
  if (tag === 'main') { return 'main'; }
  if (tag === 'header') { return 'banner'; }
  if (tag === 'footer') { return 'contentinfo'; }
  if (tag === 'ul' || tag === 'ol') { return 'list'; }
  if (tag === 'li') { return 'listitem'; }
  if (tag === 'table') { return 'table'; }
  if (tag === 'form') { return 'form'; }
  if (tag === 'dialog') { return 'dialog'; }
  return 'generic';
}
function nameOf(el){
  var label = el.getAttribute && el.getAttribute('aria-label');
  if (label) { return label; }
  var labelledBy = el.getAttribute && el.getAttribute('aria-labelledby');
  if (labelledBy) {
    try {
      var parts = [];
      var ids = labelledBy.split(' ');
      for (var i = 0; i < ids.length; i++) {
        var node = document.getElementById(ids[i]);
        if (node) { parts.push(node.textContent || ''); }
      }
      if (parts.length) { return parts.join(' ').trim(); }
    } catch (e) {}
  }
  var tag = (el.tagName || '').toLowerCase();
  if (tag === 'img') { return el.getAttribute('alt') || ''; }
  if (tag === 'input' || tag === 'select' || tag === 'textarea') {
    if (el.labels && el.labels.length) { return (el.labels[0].textContent || '').trim(); }
    return el.getAttribute('placeholder') || el.getAttribute('title') || el.name || '';
  }
  var text = (el.textContent || '').replace(/\s+/g, ' ').trim();
  if (text.length > 90) {
    var kids = el.children;
    if (kids && kids.length > 1) { return ''; }
    return text.slice(0, 90);
  }
  return text;
}
function valueOf(el){
  var value = el.getAttribute && el.getAttribute('aria-valuenow');
  if (value) { return value; }
  var tag = (el.tagName || '').toLowerCase();
  if (tag === 'input' || tag === 'textarea' || tag === 'select') {
    try { return String(el.value || '').slice(0, 60); } catch (e) { return ''; }
  }
  return '';
}
var out = [];
function walk(el, depth){
  if (!el || el.nodeType !== 1 || out.length >= 400 || depth > 24) { return; }
  var role = roleOf(el);
  var name = nameOf(el);
  var hidden = false;
  try {
    var cs = styleOf(el);
    hidden = (cs && (cs.display === 'none' || cs.visibility === 'hidden')) ||
      el.getAttribute('aria-hidden') === 'true';
  } catch (e) {}
  if (!hidden && (role !== 'generic' || name)) {
    out.push({
      role: role, name: name, value: valueOf(el), depth: depth,
      path: pathOf(el), tag: (el.tagName || '').toLowerCase()
    });
  }
  var kids = el.children;
  for (var i = 0; i < kids.length; i++) { walk(kids[i], depth + 1); }
}
try { walk(document.body || document.documentElement, 0); } catch (e) {}
return JSON.stringify({ nodes: out });
""",
    )

    /** Feature 98: two-tap contrast checker. */
    fun contrastPicking(enabled: Boolean): String = DebugJs.json(
        """
$CLEAR_LAYER
var D2 = window.__db = window.__db || {};
if (!${if (enabled) "true" else "false"}) {
  if (D2.contrastHandler) {
    document.removeEventListener('click', D2.contrastHandler, true);
    D2.contrastHandler = null;
  }
  D2.contrastPicks = [];
  return JSON.stringify({ on: false });
}
function parseColor(value){
  var match = String(value || '').match(/rgba?\(([^)]+)\)/);
  if (!match) { return null; }
  var parts = match[1].split(',').map(function(x){ return parseFloat(x); });
  return { r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 ? parts[3] : 1 };
}
function luminance(c){
  function channel(v){
    v = v / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  }
  return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
}
function contrastRatio(a, b){
  var l1 = luminance(a);
  var l2 = luminance(b);
  var hi = Math.max(l1, l2);
  var lo = Math.min(l1, l2);
  return (hi + 0.05) / (lo + 0.05);
}
function effectiveBackground(el){
  var node = el;
  while (node) {
    var color = parseColor(styleOf(node) ? styleOf(node).backgroundColor : '');
    if (color && color.a > 0.02) { return color; }
    node = node.parentElement;
  }
  return { r: 255, g: 255, b: 255, a: 1 };
}
function css(c){ return 'rgb(' + Math.round(c.r) + ',' + Math.round(c.g) + ',' + Math.round(c.b) + ')'; }
D2.contrastPicks = [];
D2.contrast = null;
D2.contrastHandler = function(ev){
  try {
    ev.preventDefault();
    ev.stopPropagation();
    var el = ev.target;
    var cs = styleOf(el);
    var label = el.getAttribute ? (el.getAttribute('aria-label') || '') : '';
    if (!label) { label = (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 60); }
    var pick = { fg: parseColor(cs.color), bg: effectiveBackground(el), label: label || (el.tagName || '') };
    if (!D2.contrastPicks) { D2.contrastPicks = []; }
    if (D2.contrastPicks.length >= 2) { D2.contrastPicks = []; }
    D2.contrastPicks.push(pick);
    if (D2.contrastPicks.length === 2) {
      var first = D2.contrastPicks[0];
      var second = D2.contrastPicks[1];
      var fg = first.fg || first.bg;
      var bg = second.bg;
      var ratio = contrastRatio(fg, bg);
      D2.contrast = {
        ratio: Math.round(ratio * 100) / 100,
        aa: ratio >= 4.5,
        aaa: ratio >= 7,
        aaLarge: ratio >= 3,
        fg: css(fg),
        bg: css(bg),
        fgLabel: first.label,
        bgLabel: second.label
      };
      D2.contrastPicks = [];
    }
  } catch (e) {}
};
document.addEventListener('click', D2.contrastHandler, true);
return JSON.stringify({ on: true });
""",
    )

    val contrastResult: String =
        "(function(){try{var D=window.__db;if(!D||!D.contrast){return 'null';}" +
            "var c=D.contrast;D.contrast=null;return JSON.stringify(c);}catch(e){return 'null';}})()"

    /** Feature 99: numbered tab order overlay. */
    fun tabOrderOverlay(enabled: Boolean): String = DebugJs.json(
        """
$CLEAR_LAYER
__dbClearLayer('__db_taborder');
if (!${if (enabled) "true" else "false"}) { return JSON.stringify({ count: 0 }); }
var selector = 'a[href], button, input, select, textarea, [tabindex], [contenteditable="true"]';
var all = Array.prototype.slice.call(document.querySelectorAll(selector));
var positive = [];
var natural = [];
var negative = [];
for (var i = 0; i < all.length; i++) {
  var el = all[i];
  if (el.disabled) { continue; }
  var r = el.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) { continue; }
  var attr = el.getAttribute('tabindex');
  var value = attr === null ? null : parseInt(attr, 10);
  if (value !== null && value < 0) { negative.push(el); }
  else if (value !== null && value > 0) { positive.push({ el: el, order: value }); }
  else { natural.push(el); }
}
positive.sort(function(a, b){ return a.order - b.order; });
var ordered = positive.map(function(x){ return x.el; }).concat(natural);
var layer = document.createElement('div');
layer.id = '__db_taborder';
layer.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483642;';
(document.body || document.documentElement).appendChild(layer);
function badge(el, text, color){
  var r = el.getBoundingClientRect();
  var b = document.createElement('div');
  b.style.cssText = 'position:fixed;z-index:2147483642;font:bold 10px monospace;color:#fff;' +
    'background:' + color + ';border-radius:8px;padding:1px 5px;' +
    'left:' + Math.max(0, r.left) + 'px;top:' + Math.max(0, r.top) + 'px;';
  b.textContent = text;
  layer.appendChild(b);
}
var count = 0;
for (var k = 0; k < ordered.length && count < 200; k++) {
  badge(ordered[k], String(k + 1), '#FF2E63');
  count++;
}
for (var n = 0; n < negative.length && n < 40; n++) {
  badge(negative[n], '-1', '#FFC857');
}
return JSON.stringify({ count: count, skipped: negative.length });
""",
    )
}
