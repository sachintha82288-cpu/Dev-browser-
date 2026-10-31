package io.github.sachintha82288.devbrowser.debug

/**
 * Rendering overlays (category 11, features 91-93).
 *
 * All three are heuristics built on MutationObserver / layout-shift entries /
 * computed styles, because GeckoView does not expose the compositor or paint
 * pipeline. Each script is fully reversible: switching the toggle off removes
 * every element it added.
 */
object RenderingScripts {

    private const val CLEAR_LAYER = """
function __dbClearLayer(id){
  var old = document.getElementById(id);
  if (old && old.parentNode) { old.parentNode.removeChild(old); }
}
"""

    /** Feature 91: flash mutated regions to approximate paint flashing. */
    fun paintFlash(enabled: Boolean): String = DebugJs.json(
        """
$CLEAR_LAYER
var D2 = window.__db = window.__db || {};
if (D2.paintObserver) { D2.paintObserver.disconnect(); D2.paintObserver = null; }
__dbClearLayer('__db_paint');
if (!${if (enabled) "true" else "false"}) { return JSON.stringify({ on: false }); }
function flash(el){
  try {
    var r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) { return; }
    var box = document.createElement('div');
    box.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483640;' +
      'background:rgba(0,229,255,0.22);border:1px solid #00E5FF;transition:opacity 300ms;' +
      'left:' + r.left + 'px;top:' + r.top + 'px;width:' + r.width + 'px;height:' + r.height + 'px;';
    (document.body || document.documentElement).appendChild(box);
    setTimeout(function(){ box.style.opacity = '0'; }, 90);
    setTimeout(function(){ if (box.parentNode) { box.parentNode.removeChild(box); } }, 430);
  } catch (e) {}
}
D2.paintObserver = new MutationObserver(function(mutations){
  var shown = 0;
  for (var i = 0; i < mutations.length && shown < 12; i++) {
    var target = mutations[i].target;
    if (target && target.nodeType === 1) { flash(target); shown++; }
  }
});
D2.paintObserver.observe(document.documentElement || document, {
  subtree: true, childList: true, attributes: true, characterData: true
});
return JSON.stringify({ on: true });
""",
    )

    /** Feature 92: persistent outlines around regions that have shifted. */
    fun shiftOutline(enabled: Boolean): String = DebugJs.json(
        """
$CLEAR_LAYER
var D2 = window.__db = window.__db || {};
if (D2.shiftObserver) { D2.shiftObserver.disconnect(); D2.shiftObserver = null; }
__dbClearLayer('__db_shifts');
if (!${if (enabled) "true" else "false"}) { return JSON.stringify({ on: false }); }
var layer = document.createElement('div');
layer.id = '__db_shifts';
layer.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483641;';
(document.body || document.documentElement).appendChild(layer);
var count = 0;
D2.shiftObserver = new PerformanceObserver(function(list){
  var entries = list.getEntries();
  for (var i = 0; i < entries.length; i++) {
    var sources = entries[i].sources || [];
    for (var s = 0; s < sources.length && count < 60; s++) {
      var node = sources[s].node;
      if (!node || !node.getBoundingClientRect) { continue; }
      var r = node.getBoundingClientRect();
      var box = document.createElement('div');
      box.style.cssText = 'position:fixed;box-sizing:border-box;border:2px solid #FFC857;' +
        'background:rgba(255,200,87,0.08);left:' + r.left + 'px;top:' + r.top + 'px;' +
        'width:' + r.width + 'px;height:' + r.height + 'px;';
      layer.appendChild(box);
      count++;
    }
  }
});
try { D2.shiftObserver.observe({ type: 'layout-shift', buffered: true }); } catch (e) {}
return JSON.stringify({ on: true });
""",
    )

    /**
     * Features 94/95: page-level emulation of `prefers-color-scheme` and
     * `prefers-reduced-motion`. GeckoView 157 offers no public device-emulation
     * API, so `matchMedia` is wrapped and the `color-scheme` property is pinned.
     * Media query *listeners* are not re-fired; new queries answer correctly.
     */
    fun media(scheme: String, motion: String): String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
if (!D2.origMatchMedia) { D2.origMatchMedia = window.matchMedia.bind(window); }
var scheme = '${scheme.replace("'", "")}';
var motion = '${motion.replace("'", "")}';
D2.forceScheme = (scheme === 'system') ? null : scheme;
D2.forceMotion = (motion === 'no-preference') ? null : motion;
if (!D2.matchPatched) {
  D2.matchPatched = true;
  window.matchMedia = function(query){
    var text = String(query || '');
    var list = D2.origMatchMedia(query);
    try {
      if (D2.forceScheme && /prefers-color-scheme/.test(text)) {
        var wantsLight = /light/.test(text);
        var dark = D2.forceScheme === 'dark';
        Object.defineProperty(list, 'matches', {
          configurable: true,
          get: function(){ return wantsLight ? !dark : dark; }
        });
      }
      if (D2.forceMotion && /prefers-reduced-motion/.test(text)) {
        var wantsReduce = /reduce/.test(text) && !/no-preference/.test(text);
        var reduced = D2.forceMotion === 'reduce';
        Object.defineProperty(list, 'matches', {
          configurable: true,
          get: function(){ return wantsReduce ? reduced : !reduced; }
        });
      }
    } catch (e) {}
    return list;
  };
}
try {
  if (D2.forceScheme) { document.documentElement.style.colorScheme = D2.forceScheme; }
  else { document.documentElement.style.removeProperty('color-scheme'); }
} catch (e) {}
return JSON.stringify({ scheme: scheme, motion: motion });
""",
    )

    /** Restores the page's own media-query behaviour. */
    val resetMedia: String = DebugJs.json(
        """
var D2 = window.__db = window.__db || {};
D2.forceScheme = null;
D2.forceMotion = null;
if (D2.origMatchMedia) { window.matchMedia = D2.origMatchMedia; }
D2.matchPatched = false;
try { document.documentElement.style.removeProperty('color-scheme'); } catch (e) {}
return JSON.stringify({ scheme: 'system', motion: 'no-preference' });
""",
    )

    /**
     * Feature 93: heuristic "composited layer" borders. Real layer promotion is
     * decided by the engine, so elements using transform / will-change / opacity /
     * filter / fixed positioning are treated as likely promoted layers.
     */
    fun layerBorders(enabled: Boolean): String = DebugJs.json(
        """
$CLEAR_LAYER
__dbClearLayer('__db_layers');
if (!${if (enabled) "true" else "false"}) { return JSON.stringify({ count: 0 }); }
var layer = document.createElement('div');
layer.id = '__db_layers';
layer.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483639;';
(document.body || document.documentElement).appendChild(layer);
var nodes = document.querySelectorAll('body *');
var count = 0;
for (var i = 0; i < nodes.length && count < 300; i++) {
  var el = nodes[i];
  var cs = styleOf(el);
  if (!cs) { continue; }
  var promoted = (cs.transform && cs.transform !== 'none') ||
    (cs.willChange && cs.willChange !== 'auto') ||
    (parseFloat(cs.opacity) < 1) ||
    cs.position === 'fixed' ||
    (cs.filter && cs.filter !== 'none') ||
    (cs.backdropFilter && cs.backdropFilter !== 'none');
  if (!promoted) { continue; }
  var r = el.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) { continue; }
  var box = document.createElement('div');
  box.style.cssText = 'position:fixed;box-sizing:border-box;border:1px solid #9D4EDD;' +
    'left:' + r.left + 'px;top:' + r.top + 'px;width:' + r.width + 'px;height:' + r.height + 'px;';
  layer.appendChild(box);
  count++;
}
return JSON.stringify({ count: count });
""",
    )
}
