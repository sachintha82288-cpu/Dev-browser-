package com.devbrowser.app.browser

import com.devbrowser.app.data.Prefs

/** Holds all open tabs and which one is in the foreground. */
object TabManager {

    private val _tabs = mutableListOf<Tab>()
    val tabs: List<Tab> get() = _tabs

    var selectedIndex: Int = -1
        private set

    val selected: Tab? get() = _tabs.getOrNull(selectedIndex)

    var listener: (() -> Unit)? = null

    fun newTab(url: String? = null, isPrivate: Boolean = false, select: Boolean = true): Tab {
        val tab = Tab(isPrivate) { listener?.invoke() }
        _tabs.add(tab)
        tab.open()
        tab.load(url ?: Prefs.homePage)
        if (select) selectedIndex = _tabs.lastIndex
        listener?.invoke()
        return tab
    }

    fun select(index: Int) {
        if (index in _tabs.indices) {
            selectedIndex = index
            listener?.invoke()
        }
    }

    fun close(index: Int) {
        if (index !in _tabs.indices) return
        _tabs.removeAt(index).close()
        if (_tabs.isEmpty()) {
            selectedIndex = -1
            newTab()
        } else {
            selectedIndex = selectedIndex.coerceAtMost(_tabs.lastIndex)
            listener?.invoke()
        }
    }

    fun closeAll() {
        _tabs.forEach { it.close() }
        _tabs.clear()
        selectedIndex = -1
        listener?.invoke()
    }
}
