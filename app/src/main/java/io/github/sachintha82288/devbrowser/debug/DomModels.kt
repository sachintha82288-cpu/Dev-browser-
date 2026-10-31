package io.github.sachintha82288.devbrowser.debug

/** A node in the serialized DOM tree of the inspected page. */
data class DomNode(
    val id: Int,
    val tag: String,
    val path: String,
    val htmlId: String,
    val classes: String,
    val text: String,
    val childCount: Int,
    val children: List<DomNode>,
)

/** Everything the panels need to know about the currently selected element. */
data class DomNodeInfo(
    val id: Int,
    val path: String,
    val tag: String,
    val selector: String,
    val html: String,
    val attributes: List<DomAttribute>,
    val text: String,
    val rect: ElementRect,
)

data class DomAttribute(val name: String, val value: String)

data class ElementRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** One CSS declaration. */
data class CssDeclaration(val name: String, val value: String)

/** A matched CSS rule for the selected element. */
data class CssRule(
    val selector: String,
    val source: String,
    val origin: String,
    val declarations: List<CssDeclaration>,
)

/** Computed margin / border / padding / content measurements. */
data class BoxModel(
    val margin: Edges,
    val border: Edges,
    val padding: Edges,
    val contentWidth: Int,
    val contentHeight: Int,
)

data class Edges(val top: Int, val right: Int, val bottom: Int, val left: Int)
