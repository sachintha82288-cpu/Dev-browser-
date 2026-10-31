package io.github.sachintha82288.devbrowser.debug

/**
 * Viewport / device emulation scripts (category 9).
 *
 * GeckoView 157 has no public "device emulation" API, so the app applies the
 * closest available workaround: constrain the document width, override
 * `innerWidth` / `innerHeight` / `devicePixelRatio`, and optionally synthesise
 * touch events. Layout-affecting media queries are evaluated by the engine
 * against the real viewport, which is reported in the UI as a limitation.
 */
object ResponsiveScripts {

    fun apply(width: Int, height: Int, dpr: Float, touch: Boolean): String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
D2.viewport = { w: $width, h: $height, dpr: $dpr, touch: ${if (touch) "true" else "false"} };
var style = document.getElementById('__db_viewport_style');
if (!style) {
  style = document.createElement('style');
  style.id = '__db_viewport_style';
  (document.head || document.documentElement).appendChild(style);
}
style.textContent =
  'html{width:' + $width + 'px !important;max-width:' + $width + 'px !important;}' +
  'body{min-width:0 !important;}';
try {
  Object.defineProperty(window, 'innerWidth', { configurable: true, get: function(){ return $width; } });
  Object.defineProperty(window, 'innerHeight', { configurable: true, get: function(){ return $height; } });
  Object.defineProperty(window, 'outerWidth', { configurable: true, get: function(){ return $width; } });
  Object.defineProperty(window, 'outerHeight', { configurable: true, get: function(){ return $height; } });
  Object.defineProperty(window, 'devicePixelRatio', { configurable: true, get: function(){ return $dpr; } });
} catch (e) {}
if (${if (touch) "true" else "false"}) {
  try {
    window.ontouchstart = null;
    if (!document.__dbTouch) {
      document.__dbTouch = function(ev){
        try {
          var t = document.createTouch ? null : null;
          var touch = new Touch({
            identifier: 1, target: ev.target,
            clientX: ev.clientX, clientY: ev.clientY,
            pageX: ev.pageX, pageY: ev.pageY, screenX: ev.screenX, screenY: ev.screenY
          });
          var name = ev.type === 'mousedown' ? 'touchstart' : (ev.type === 'mouseup' ? 'touchend' : 'touchmove');
          ev.target.dispatchEvent(new TouchEvent(name, {
            bubbles: true, cancelable: true, touches: name === 'touchend' ? [] : [touch],
            targetTouches: name === 'touchend' ? [] : [touch], changedTouches: [touch]
          }));
        } catch (e) {}
      };
      document.addEventListener('mousedown', document.__dbTouch, true);
      document.addEventListener('mouseup', document.__dbTouch, true);
    }
  } catch (e) {}
}
return JSON.stringify({ w: $width, h: $height, dpr: $dpr });
""",
    )

    val reset: String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
D2.viewport = null;
var style = document.getElementById('__db_viewport_style');
if (style && style.parentNode) { style.parentNode.removeChild(style); }
try {
  delete window.innerWidth;
  delete window.innerHeight;
  delete window.outerWidth;
  delete window.outerHeight;
  delete window.devicePixelRatio;
} catch (e) {}
try {
  if (document.__dbTouch) {
    document.removeEventListener('mousedown', document.__dbTouch, true);
    document.removeEventListener('mouseup', document.__dbTouch, true);
    document.__dbTouch = null;
  }
  if (window.ontouchstart !== undefined) { delete window.ontouchstart; }
} catch (e) {}
return JSON.stringify({ reset: true });
""",
    )

    /** Feature 80: every @media rule with a match indicator. */
    val mediaQueries: String = DebugJs.json(
        """
var out = [];
var sheets = document.styleSheets || [];
for (var s = 0; s < sheets.length; s++) {
  var rules = null;
  try { rules = sheets[s].cssRules; } catch (e) { rules = null; }
  if (!rules) { continue; }
  for (var i = 0; i < rules.length; i++) {
    var rule = rules[i];
    var condition = '';
    if (rule.media && rule.media.mediaText) { condition = rule.media.mediaText; }
    else if (rule.conditionText) { condition = rule.conditionText; }
    if (!condition) { continue; }
    var matched = false;
    try { matched = window.matchMedia(condition).matches; } catch (e) { matched = false; }
    out.push({ q: condition, m: matched, src: (sheets[s].href || 'inline').split('/').pop() });
    if (out.length > 200) { break; }
  }
}
return JSON.stringify({ queries: out, viewport: { w: window.innerWidth, h: window.innerHeight } });
""",
    )

    /** Feature 81: viewport bounds + scrollbar sizes overlay. */
    fun overlay(enabled: Boolean): String = DebugJs.json(
        """
var id = '__db_viewport_overlay';
var el = document.getElementById(id);
if (!${if (enabled) "true" else "false"}) {
  if (el && el.parentNode) { el.parentNode.removeChild(el); }
  return JSON.stringify({ on: false });
}
if (!el) {
  el = document.createElement('div');
  el.id = id;
  el.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483645;' +
    'border:2px dashed #9D4EDD;box-sizing:border-box;';
  var label = document.createElement('div');
  label.id = '__db_viewport_label';
  label.style.cssText = 'position:absolute;left:0;top:0;background:#9D4EDD;color:#fff;' +
    'font:11px monospace;padding:2px 4px;';
  el.appendChild(label);
  (document.body || document.documentElement).appendChild(el);
}
var scrollbarW = Math.max(0, Math.round(window.innerWidth - document.documentElement.clientWidth));
var scrollbarH = Math.max(0, Math.round(window.innerHeight - document.documentElement.clientHeight));
el.style.left = '0px';
el.style.top = '0px';
el.style.width = window.innerWidth + 'px';
el.style.height = window.innerHeight + 'px';
var lbl = document.getElementById('__db_viewport_label');
if (lbl) { lbl.textContent = window.innerWidth + 'x' + window.innerHeight + ' scrollbars ' + scrollbarW + '/' + scrollbarH; }
return JSON.stringify({ on: true, w: window.innerWidth, h: window.innerHeight, scrollbarW: scrollbarW, scrollbarH: scrollbarH });
""",
    )

    /** Feature 82: two-tap measurement ruler. */
    fun ruler(enabled: Boolean): String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
if (!${if (enabled) "true" else "false"}) {
  if (D2.rulerHandler) {
    document.removeEventListener('click', D2.rulerHandler, true);
    D2.rulerHandler = null;
  }
  var old = document.getElementById('__db_ruler');
  if (old && old.parentNode) { old.parentNode.removeChild(old); }
  D2.ruler = null;
  return JSON.stringify({ on: false });
}
if (!D2.rulerHandler) {
  D2.ruler = { a: null, b: null, dist: 0 };
  D2.rulerHandler = function(ev){
    try {
      ev.preventDefault();
      ev.stopPropagation();
      var point = { x: Math.round(ev.clientX), y: Math.round(ev.clientY) };
      if (!D2.ruler.a || (D2.ruler.a && D2.ruler.b)) {
        D2.ruler = { a: point, b: null, dist: 0 };
      } else {
        D2.ruler.b = point;
        var dx = point.x - D2.ruler.a.x;
        var dy = point.y - D2.ruler.a.y;
        D2.ruler.dist = Math.round(Math.sqrt(dx * dx + dy * dy));
      }
      var layer = document.getElementById('__db_ruler');
      if (!layer) {
        layer = document.createElement('div');
        layer.id = '__db_ruler';
        layer.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483645;';
        (document.body || document.documentElement).appendChild(layer);
      }
      layer.innerHTML = '';
      function dot(p, color){
        var d = document.createElement('div');
        d.style.cssText = 'position:absolute;width:10px;height:10px;border-radius:5px;' +
          'background:' + color + ';left:' + (p.x - 5) + 'px;top:' + (p.y - 5) + 'px;';
        layer.appendChild(d);
      }
      if (D2.ruler.a) { dot(D2.ruler.a, '#FF2E63'); }
      if (D2.ruler.b) {
        dot(D2.ruler.b, '#00E5FF');
        var ax = D2.ruler.a.x, ay = D2.ruler.a.y, bx = D2.ruler.b.x, by = D2.ruler.b.y;
        var len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
        var angle = Math.atan2(by - ay, bx - ax) * 180 / Math.PI;
        var line = document.createElement('div');
        line.style.cssText = 'position:absolute;height:2px;background:#FF2E63;transform-origin:0 0;' +
          'left:' + ax + 'px;top:' + ay + 'px;width:' + len + 'px;' +
          'transform:rotate(' + angle + 'deg);';
        layer.appendChild(line);
        var label = document.createElement('div');
        label.style.cssText = 'position:absolute;background:#FF2E63;color:#fff;font:11px monospace;' +
          'padding:2px 4px;left:' + ((ax + bx) / 2) + 'px;top:' + ((ay + by) / 2) + 'px;';
        label.textContent = Math.round(len) + ' px';
        layer.appendChild(label);
      }
    } catch (e) {}
  };
  document.addEventListener('click', D2.rulerHandler, true);
}
return JSON.stringify({ on: true });
""",
    )

    val rulerResult: String =
        "(function(){try{var D=window.__db;if(!D||!D.ruler){return 'null';}" +
            "return JSON.stringify(D.ruler);}catch(e){return 'null';}})()"
}
