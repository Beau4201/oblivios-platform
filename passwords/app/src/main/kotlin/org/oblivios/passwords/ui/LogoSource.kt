// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap
import org.oblivios.passwords.PasswordsApplication

/** A real logo: the icon of an installed app, or a shape of the bundled pack (drawn in the text colour of the theme). */
sealed interface Logo {
    class Image(val image: ImageBitmap) : Logo
    class Shape(val path: Path) : Logo
}

/**
 * Where the logo of an entry comes from, in this order (spec 4.2): the icon of an app the entry is linked to, a shape of
 * the bundled pack for one of its sites, else nothing (the tile then shows the generated pattern). Nothing is fetched.
 */
class LogoSource(private val context: Context) {
    private val pack: LogoPack by lazy {
        fun asset(name: String) = runCatching { context.assets.open("logos/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
        LogoPack.parse(asset("domains.tsv"), asset("icons.tsv"))
    }
    private val cache = ConcurrentHashMap<String, Any>()

    fun logoFor(domains: List<String>, packages: List<String>): Logo? {
        for (p in packages) appIcon(p)?.let { return Logo.Image(it) }
        for (d in domains) {
            val key = "d:$d"
            val hit = cache.getOrPut(key) { pack.pathFor(d)?.let { runCatching { PathParser().parsePathString(it).toPath() }.getOrNull() } ?: NONE }
            if (hit is Path) return Logo.Shape(hit)
        }
        return null
    }

    private fun appIcon(pkg: String): ImageBitmap? {
        val hit = cache.getOrPut("p:$pkg") {
            runCatching {
                val d = context.packageManager.getApplicationIcon(pkg)
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 96, 96)
                d.draw(Canvas(bmp))
                bmp.asImageBitmap()
            }.getOrNull() ?: NONE
        }
        return hit as? ImageBitmap
    }

    private companion object {
        val NONE = Any()
    }
}

/** The logo for an entry, computed once per list row. */
@Composable
fun rememberLogo(domains: List<String>, packages: List<String>): Logo? {
    val ctx = LocalContext.current
    return remember(domains, packages) { (ctx.applicationContext as PasswordsApplication).logos.logoFor(domains, packages) }
}
