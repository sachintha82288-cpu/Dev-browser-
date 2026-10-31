package io.github.sachintha82288.devbrowser.browser

import org.json.JSONObject

/** Extracted article shown by the Stage A reader view. */
data class ReaderArticle(
    val title: String,
    val byline: String,
    val siteName: String,
    val paragraphs: List<String>,
    val url: String,
) {
    /** Rough reading time at 200 words per minute. */
    val minutes: Int
        get() = ((paragraphs.sumOf { it.split(' ').size } / 200).coerceAtLeast(1))
}

/**
 * Stage A: reader-mode extraction.
 *
 * Picks the densest `<article>`/`<main>`/`<div>` by paragraph text, copies its
 * paragraphs and headings, and reads title/byline from metadata. Runs entirely
 * in the page, so nothing leaves the device.
 */
object ReaderMode {

    val extractScript: String = """
(function(){
  try {
    function text(el){ return (el && el.textContent ? el.textContent : '').replace(/\s+/g, ' ').trim(); }
    function meta(selector){
      var el = document.querySelector(selector);
      if (!el) { return ''; }
      return el.getAttribute('content') || el.getAttribute('value') || '';
    }
    var candidates = [];
    var roots = document.querySelectorAll('article, main, [role="main"], .post, .entry-content, #content');
    if (roots.length === 0) { roots = [document.body]; }
    for (var r = 0; r < roots.length; r++) {
      if (!roots[r]) { continue; }
      var ps = roots[r].querySelectorAll('p');
      var score = 0;
      var count = 0;
      for (var p = 0; p < ps.length; p++) {
        var t = text(ps[p]);
        if (t.length > 40) { score += t.length; count++; }
      }
      candidates.push({ root: roots[r], score: score, count: count });
    }
    candidates.sort(function(a, b){ return b.score - a.score; });
    var best = candidates[0];
    var paragraphs = [];
    if (best && best.score > 200) {
      var nodes = best.root.querySelectorAll('p, h2, h3, blockquote, li');
      for (var n = 0; n < nodes.length && paragraphs.length < 250; n++) {
        var value = text(nodes[n]);
        if (value.length < 30) { continue; }
        if (paragraphs.indexOf(value) !== -1) { continue; }
        paragraphs.push(value.slice(0, 4000));
      }
    }
    var title = meta('meta[property="og:title"]') || meta('meta[name="twitter:title"]') ||
      (document.querySelector('h1') ? text(document.querySelector('h1')) : '') || document.title || '';
    var byline = meta('meta[name="author"]') || meta('meta[property="article:author"]') || '';
    if (!byline) {
      var authorEl = document.querySelector('[rel="author"], .byline, .author, [class*="author"]');
      if (authorEl) { byline = text(authorEl).slice(0, 120); }
    }
    var site = meta('meta[property="og:site_name"]') || (location.hostname || '');
    return JSON.stringify({
      title: String(title).slice(0, 300),
      byline: String(byline).slice(0, 200),
      site: String(site).slice(0, 160),
      paragraphs: paragraphs,
      url: location.href
    });
  } catch (e) {
    return JSON.stringify({ error: String((e && e.message) || e) });
  }
})()
""".trim()

    fun parse(payload: JSONObject?, fallbackUrl: String?): ReaderArticle? {
        if (payload == null) return null
        if (payload.has("error")) return null
        val array = payload.optJSONArray("paragraphs") ?: return null
        val paragraphs = ArrayList<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotEmpty()) paragraphs.add(value)
        }
        if (paragraphs.size < 2) return null
        return ReaderArticle(
            title = payload.optString("title").ifBlank { "Untitled" },
            byline = payload.optString("byline"),
            siteName = payload.optString("site"),
            paragraphs = paragraphs,
            url = payload.optString("url").ifBlank { fallbackUrl ?: "" },
        )
    }
}
