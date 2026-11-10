package app.shelf.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutLogicTest {
    private fun app(name: String, pkg: String = "pkg.$name", leanback: Boolean = true) =
        AppEntry("$pkg/.Main", pkg, "$pkg.Main", name, leanback, stamp = 0, hasBanner = true)

    private val a = app("Alpha"); private val b = app("Bravo"); private val c = app("Charlie")
    private val d = app("Delta"); private val e = app("Echo"); private val f = app("Foxtrot")
    private val apps = listOf(a, b, c, d, e, f)              // already A–Z, as AppRepository returns them

    private fun keys(l: List<AppEntry>) = l.map { it.label.first() }.joinToString("")
    private fun arranged(l: Layout, cap: Int = 3) = LayoutLogic.arrange(apps, l, cap)

    // ---- arrange ----
    @Test fun `empty layout lists every app alphabetically with no favourites`() {
        val r = arranged(Layout())
        assertEquals("", keys(r.dock)); assertEquals("ABCDEF", keys(r.main))
    }

    @Test fun `favourites come first in saved order, then saved order, then new apps`() {
        val r = arranged(Layout(favorites = listOf(c.key, a.key), order = listOf(f.key, b.key)))
        assertEquals("CA", keys(r.dock)); assertEquals("FBDE", keys(r.main))
    }

    @Test fun `hidden apps never appear, even if they are favourites or ordered`() {
        val r = arranged(Layout(favorites = listOf(a.key), order = listOf(b.key), hidden = setOf(a.key, b.key)))
        assertEquals("", keys(r.dock)); assertEquals("CDEF", keys(r.main))
    }

    @Test fun `favourites beyond the dock capacity overflow to the front of the list`() {
        val r = arranged(Layout(favorites = listOf(a.key, b.key, c.key, d.key)), cap = 2)
        assertEquals("AB", keys(r.dock)); assertEquals("CDEF", keys(r.main))
    }

    @Test fun `uninstalled and duplicate keys are ignored`() {
        val r = arranged(Layout(favorites = listOf("gone/.X", b.key, b.key), order = listOf(c.key, c.key)))
        assertEquals("B", keys(r.dock)); assertEquals("CADEF", keys(r.main))
        assertEquals(apps.size, r.dock.size + r.main.size)
    }

    // ---- seed ----
    @Test fun `seed picks known streaming apps as favourites and hides TCL utilities`() {
        val netflix = app("Netflix", "com.netflix.ninja"); val spotify = app("Spotify", "com.spotify.tv.android")
        val ota = app("OTA", "com.tcl.rc.ota", leanback = false)
        val l = LayoutLogic.seed(listOf(a, netflix, ota, spotify), columns = 5)
        assertEquals(listOf(netflix.key, spotify.key), l.favorites)
        assertEquals(setOf(ota.key), l.hidden)
        assertTrue(l.seeded)
    }

    @Test fun `seed falls back to the first TV apps when no known apps are installed`() {
        val phoneOnly = app("Zed", leanback = false)
        val l = LayoutLogic.seed(listOf(phoneOnly, a, b), columns = 1)
        assertEquals(listOf(a.key), l.favorites)
    }

    // ---- grid move ----
    @Test fun `grid move left and right swaps neighbours and stops at the ends`() {
        val l = Layout()
        val r = LayoutLogic.move(l, b.key, Dir.LEFT, arranged(l), columns = 3)!!
        assertEquals("BACDEF", keys(arranged(r).main))
        assertNull(LayoutLogic.move(l, a.key, Dir.LEFT, arranged(l), 3))
        assertNull(LayoutLogic.move(l, f.key, Dir.RIGHT, arranged(l), 3))
    }

    @Test fun `grid move up from the first row joins favourites, down leaves them`() {
        val l = Layout(favorites = listOf(a.key))
        val up = LayoutLogic.move(l, c.key, Dir.UP, arranged(l), columns = 3)!!
        assertEquals("AC", keys(arranged(up).dock))
        val down = LayoutLogic.move(up, c.key, Dir.DOWN, arranged(up), columns = 3)!!
        assertEquals("A", keys(arranged(down).dock)); assertTrue(c.key in arranged(down).main.map { it.key })
    }

    @Test fun `grid move up is refused when the favourites row is full`() {
        val l = Layout(favorites = listOf(a.key, b.key, c.key))
        assertNull(LayoutLogic.move(l, d.key, Dir.UP, arranged(l), columns = 3))
    }

    @Test fun `grid move down jumps a row and is refused on the last row`() {
        val l = Layout()                                     // main: A B C / D E F  (3 columns)
        val r = LayoutLogic.move(l, a.key, Dir.DOWN, arranged(l), columns = 3)!!
        assertEquals("BCDAEF", keys(arranged(r).main))
        assertNull(LayoutLogic.move(l, e.key, Dir.DOWN, arranged(l), columns = 3))
    }

    @Test fun `grid move of an unknown key does nothing`() {
        assertNull(LayoutLogic.move(Layout(), "nope", Dir.RIGHT, arranged(Layout()), 3))
    }

    // ---- linear (carousel / wave) move ----
    @Test fun `linear move across the divider favourites and un-favourites`() {
        val l = Layout(favorites = listOf(a.key))
        val fav = LayoutLogic.moveLinear(l, b.key, Dir.LEFT, arranged(l, 12), crossDivider = true)!!
        assertEquals("AB", keys(arranged(fav, 12).dock))
        val unfav = LayoutLogic.moveLinear(fav, b.key, Dir.RIGHT, arranged(fav, 12), crossDivider = true)!!
        assertEquals("A", keys(arranged(unfav, 12).dock)); assertEquals('B', keys(arranged(unfav, 12).main).first())
    }

    @Test fun `linear move without crossing stops at the favourites boundary`() {
        val l = Layout(favorites = listOf(a.key))
        assertNull(LayoutLogic.moveLinear(l, b.key, Dir.LEFT, arranged(l, 12), crossDivider = false))
        assertNull(LayoutLogic.moveLinear(l, a.key, Dir.RIGHT, arranged(l, 12), crossDivider = false))
        val r = LayoutLogic.moveLinear(l, c.key, Dir.LEFT, arranged(l, 12), crossDivider = false)!!
        assertEquals("CBDEF", keys(arranged(r, 12).main))
    }

    @Test fun `linear move ignores up and down`() {
        assertNull(LayoutLogic.moveLinear(Layout(), a.key, Dir.UP, arranged(Layout()), true))
        assertNull(LayoutLogic.moveLinear(Layout(), a.key, Dir.DOWN, arranged(Layout()), true))
    }

    @Test fun `moves keep every app exactly once`() {
        var l = Layout(favorites = listOf(a.key, b.key))
        val dirs = listOf(Dir.LEFT, Dir.LEFT, Dir.RIGHT, Dir.LEFT, Dir.RIGHT, Dir.RIGHT, Dir.RIGHT, Dir.LEFT)
        for (dir in dirs) l = LayoutLogic.moveLinear(l, d.key, dir, arranged(l, 12), true) ?: l
        val all = arranged(l, 12).let { it.dock + it.main }.map { it.key }
        assertEquals(apps.map { it.key }.sorted(), all.sorted())
    }

    // ---- favourites / hide ----
    @Test fun `toggle favourite adds, removes, and bumps the last favourite when full`() {
        val l = Layout(favorites = listOf(a.key, b.key))
        val added = LayoutLogic.toggleFavorite(l, c.key, arranged(l, 2), dockCap = 2)
        assertEquals(listOf(a.key, c.key), added.favorites); assertEquals(b.key, added.order.first())
        val removed = LayoutLogic.toggleFavorite(added, a.key, arranged(added, 2), dockCap = 2)
        assertEquals(listOf(c.key), removed.favorites); assertEquals(a.key, removed.order.first())
    }

    @Test fun `hide removes from favourites and order, unhide brings it back`() {
        val l = Layout(favorites = listOf(a.key), order = listOf(b.key))
        val hidden = LayoutLogic.hide(l, a.key)
        assertFalse(a.key in hidden.favorites); assertTrue(a.key in hidden.hidden)
        assertFalse(a.key in arranged(hidden).let { it.dock + it.main }.map { it.key })
        val back = LayoutLogic.unhide(hidden, a.key)
        assertTrue(a.key in arranged(back).main.map { it.key })
    }

    // ---- styles ----
    @Test fun `style cycle visits every style, wraps, and restarts for unknown styles`() {
        var s = STYLE_AERO
        val seen = mutableListOf<String>()
        repeat(LayoutLogic.STYLE_ORDER.size) { seen += s; s = LayoutLogic.nextStyle(s) }
        assertEquals(LayoutLogic.STYLE_ORDER, seen); assertEquals(STYLE_AERO, s)
        assertEquals(STYLE_AERO, LayoutLogic.nextStyle("something-old"))
    }

    @Test fun `wave is the default style`() {
        assertEquals(STYLE_WAVE, Settings().style)
        assertFalse(Settings().carousel)
        assertTrue(Settings(style = STYLE_AERO_NIGHT).carousel)
    }
}
