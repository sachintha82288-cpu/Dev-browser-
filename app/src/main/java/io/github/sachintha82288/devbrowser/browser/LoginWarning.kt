package io.github.sachintha82288.devbrowser.browser

import org.json.JSONObject

/**
 * Stage B: insecure-login detection.
 *
 * Any visible password field on a non-HTTPS page means credentials would be
 * sent in clear text. The page is asked for its own protocol, so an https page
 * that merely contains a form is never flagged.
 */
object LoginWarning {

    val script: String = """
(function(){
  try {
    var inputs = document.querySelectorAll('input[type="password"]');
    var visible = 0;
    for (var i = 0; i < inputs.length; i++) {
      var rect = inputs[i].getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) { visible++; }
    }
    var insecure = location.protocol !== 'https:' && visible > 0;
    return JSON.stringify({
      fields: visible,
      insecure: insecure,
      host: location.hostname || ''
    });
  } catch (e) { return 'null'; }
})()
""".trim()

    /**
     * Returns the number of clear-text password fields, or 0 when the page is
     * secure or reports nothing.
     */
    fun insecurePasswordFields(payload: JSONObject?): Int {
        if (payload == null) return 0
        return if (payload.optBoolean("insecure", false)) payload.optInt("fields", 0) else 0
    }
}
