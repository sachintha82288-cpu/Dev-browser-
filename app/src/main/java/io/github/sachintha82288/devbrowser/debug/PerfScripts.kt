package io.github.sachintha82288.devbrowser.debug

/**
 * Performance instrumentation (category 6).
 *
 * Observers for LCP / INP / CLS / long tasks are installed once per page load so
 * the values are complete (they cannot be back-filled reliably). The FPS sampler
 * only runs while a perf overlay or the performance panel is open.
 */
object PerfScripts {

    /** Installed on every page load when debug panels are enabled. */
    val installObservers: String = """
(function(){
try {
  var D = window.__db = window.__db || {};
  if (!D.perf) { D.perf = { lcp: 0, cls: 0, inp: 0, longTasks: [], fps: 0 }; }
  if (D.perfInstalled) { return 'ok'; }
  D.perfInstalled = true;
  function box(node){
    try {
      var r = node.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) { return; }
      var el = document.createElement('div');
      el.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;' +
        'border:2px solid #FFC857;background:rgba(255,200,87,0.22);' +
        'transition:opacity 200ms ease-out;opacity:1;' +
        'left:' + r.left + 'px;top:' + r.top + 'px;width:' + r.width + 'px;height:' + r.height + 'px;';
      (document.body || document.documentElement).appendChild(el);
      setTimeout(function(){ el.style.opacity = '0'; }, 40);
      setTimeout(function(){ if (el.parentNode) { el.parentNode.removeChild(el); } }, 260);
    } catch (e) {}
  }
  try {
    new PerformanceObserver(function(list){
      var entries = list.getEntries();
      for (var i = 0; i < entries.length; i++) {
        var e = entries[i];
        if (e.hadRecentInput) { continue; }
        D.perf.cls += e.value;
        if (D.perf.flashShifts && e.sources) {
          for (var s = 0; s < e.sources.length; s++) {
            if (e.sources[s].node) { box(e.sources[s].node); }
          }
        }
      }
    }).observe({ type: 'layout-shift', buffered: true });
  } catch (e) {}
  try {
    new PerformanceObserver(function(list){
      var entries = list.getEntries();
      if (entries.length) { D.perf.lcp = entries[entries.length - 1].startTime; }
    }).observe({ type: 'largest-contentful-paint', buffered: true });
  } catch (e) {}
  try {
    new PerformanceObserver(function(list){
      var entries = list.getEntries();
      for (var i = 0; i < entries.length; i++) {
        if (entries[i].duration > D.perf.inp) { D.perf.inp = entries[i].duration; }
      }
    }).observe({ type: 'event', buffered: true, durationThreshold: 40 });
  } catch (e) {}
  try {
    new PerformanceObserver(function(list){
      var entries = list.getEntries();
      for (var i = 0; i < entries.length; i++) {
        var e = entries[i];
        D.perf.longTasks.push({ d: Math.round(e.duration), t: Math.round(e.startTime) });
        if (D.perf.longTasks.length > 200) { D.perf.longTasks.shift(); }
        if (D.perf.logLongTasks) {
          try { console.warn('[longtask] ' + Math.round(e.duration) + ' ms at ' + Math.round(e.startTime) + ' ms'); } catch (e2) {}
        }
      }
    }).observe({ type: 'longtask', buffered: true });
  } catch (e) {}
  return 'ok';
} catch (e) { return 'err'; }
})()
""".trim()

    /** Feature 45: requestAnimationFrame FPS sampler. */
    fun setFpsSampler(enabled: Boolean): String = """
(function(){
try {
  var D = window.__db = window.__db || {};
  if (!D.perf) { D.perf = { lcp: 0, cls: 0, inp: 0, longTasks: [], fps: 0 }; }
  if (!${if (enabled) "true" else "false"}) { D.fpsRunning = false; D.perf.fps = 0; return 'off'; }
  if (D.fpsRunning) { return 'ok'; }
  D.fpsRunning = true;
  var frames = 0;
  var last = performance.now();
  function tick(now){
    if (!D.fpsRunning) { return; }
    frames++;
    if (now - last >= 1000) {
      D.perf.fps = Math.round(frames * 1000 / (now - last));
      frames = 0;
      last = now;
    }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
  return 'ok';
} catch (e) { return 'err'; }
})()
""".trim()

    /** Feature 50: mirror long tasks into the console. */
    fun setLongTaskLogging(enabled: Boolean): String =
        "(function(){try{var D=window.__db;if(!D){return 'err';}D.perf=D.perf||{};" +
            "D.perf.logLongTasks=${if (enabled) "true" else "false"};return 'ok';}catch(e){return 'err';}})()"

    /** Feature 51: flash rectangles for layout shifts. */
    fun setShiftFlash(enabled: Boolean): String =
        "(function(){try{var D=window.__db;if(!D){return 'err';}D.perf=D.perf||{};" +
            "D.perf.flashShifts=${if (enabled) "true" else "false"};return 'ok';}catch(e){return 'err';}})()"

    val snapshot: String = DebugJs.json(
        """
var nav = null;
try {
  var n = performance.getEntriesByType('navigation')[0];
  if (n) {
    nav = {
      dns: Math.round(n.domainLookupEnd - n.domainLookupStart),
      tcp: Math.round(n.connectEnd - n.connectStart),
      tls: n.secureConnectionStart > 0 ? Math.round(n.connectEnd - n.secureConnectionStart) : 0,
      ttfb: Math.round(n.responseStart - n.requestStart),
      dcl: Math.round(n.domContentLoadedEventEnd),
      dom: Math.round(n.domInteractive),
      load: Math.round(n.loadEventEnd)
    };
  }
} catch (e) {}
var paints = {};
try {
  var pe = performance.getEntriesByType('paint');
  for (var i = 0; i < pe.length; i++) { paints[pe[i].name] = Math.round(pe[i].startTime); }
} catch (e) {}
var res = [];
try {
  var re = performance.getEntriesByType('resource');
  for (var j = 0; j < re.length && j < 400; j++) {
    var r = re[j];
    res.push({
      u: r.name, ty: r.initiatorType || '',
      tr: r.transferSize || 0, de: r.decodedBodySize || 0,
      d: Math.round(r.duration || 0)
    });
  }
} catch (e) {}
var P = (window.__db && window.__db.perf) || { lcp: 0, cls: 0, inp: 0, longTasks: [], fps: 0 };
var heap = null;
try { if (performance.memory) { heap = performance.memory.usedJSHeapSize; } } catch (e) {}
return JSON.stringify({
  nav: nav, paints: paints, resources: res,
  vitals: { lcp: Math.round(P.lcp || 0), inp: Math.round(P.inp || 0), cls: Math.round((P.cls || 0) * 1000) / 1000 },
  fps: P.fps || 0, heap: heap, longTasks: (P.longTasks || []).slice(-100)
});
""",
    )
}
