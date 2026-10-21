// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.freeholdos.passwords.R

/** The 26 dp the gesture handle needs under bars, footers and sheets (same inset as the other references). */
val GestureInset = 26.dp

/** A drawable of the icon set, tinted. */
@Composable
fun PwIcon(res: Int, modifier: Modifier = Modifier, tint: Color = pw.onVariant, size: Dp = 24.dp) {
    androidx.compose.foundation.Image(
        painter = painterResource(res), contentDescription = null, modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** An icon that can be tapped; 48 dp target, with a spoken label. */
@Composable
fun IconAction(res: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = pw.onVariant) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { PwIcon(res, tint = tint) }
}

/** The site logo on a neutral tile: the real logo when there is one, else the generated pattern of the name. */
@Composable
fun LogoTile(name: String, size: Dp, modifier: Modifier = Modifier, logo: Logo? = null) {
    val c = pw
    val p = patternFor(name)
    val (tile, cells) = if (c.dark) PatternColors.dark(p.hueIndex) else PatternColors.light(p.hueIndex)
    val shape = RoundedCornerShape(12.dp)
    val neutral = logo != null
    Box(
        modifier.size(size).clip(shape).background(if (neutral) c.highest else Color(tile)).border(1.dp, c.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        when (logo) {
            is Logo.Image -> androidx.compose.foundation.Image(bitmap = logo.image, contentDescription = null, modifier = Modifier.size(size * 0.7f))
            is Logo.Shape -> Canvas(Modifier.size(size * 0.6f)) {
                scale(this.size.width / 24f, pivot = Offset.Zero) { drawPath(logo.path, c.on) }
            }
            null -> Canvas(Modifier.size(size * 0.6f)) {
                val cell = this.size.width / 5f
                p.rows.forEachIndexed { y, row ->
                    row.forEachIndexed { x, ch ->
                        if (ch == '#') drawRectCell(Color(cells), Offset(x * cell, y * cell), Size(cell, cell))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRectCell(color: Color, topLeft: Offset, size: Size) =
    drawRect(color, topLeft, size)

/** The slot for the app logo (none exists yet): a tonal tile with the lock icon. */
@Composable
fun AppLogoTile(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(pw.secondaryContainer), contentAlignment = Alignment.Center) {
        PwIcon(R.drawable.ic_lock, tint = pw.onSecondaryContainer, size = size / 2)
    }
}

/** Rows that sit together in one rounded group: 20 dp outer corners, 4 dp inner, 2 dp gap, 16 dp side margin. */
@Composable
fun GroupHeader(text: String) {
    Text(
        text, color = pw.primary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable GroupScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val items = mutableListOf<@Composable () -> Unit>()
        object : GroupScope {
            override fun item(content: @Composable () -> Unit) { items.add(content) }
        }.content()
        items.forEachIndexed { i, item ->
            val outer = 20.dp
            val inner = 4.dp
            val shape = RoundedCornerShape(
                topStart = if (i == 0) outer else inner, topEnd = if (i == 0) outer else inner,
                bottomStart = if (i == items.lastIndex) outer else inner, bottomEnd = if (i == items.lastIndex) outer else inner,
            )
            Box(Modifier.fillMaxWidth().clip(shape).background(pw.row)) { item() }
        }
    }
}

interface GroupScope {
    fun item(content: @Composable () -> Unit)
}

/** A settings-style row: optional icon, a title with a supporting line, optional trailing content. */
@Composable
fun PrefRow(
    title: String, modifier: Modifier = Modifier, summary: String? = null, icon: Int? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 72.dp).let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PwIcon(icon)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = pw.on, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(summary, color = pw.onVariant, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun PwSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = pw.onPrimary, checkedTrackColor = pw.primary,
            uncheckedThumbColor = pw.outline, uncheckedTrackColor = pw.highest, uncheckedBorderColor = pw.outline,
        ),
    )
}

/** Strength as text plus bars, never colour alone. */
@Composable
fun StrengthBars(level: Int, modifier: Modifier = Modifier) {
    val label = when (level) {
        0 -> R.string.pm_strength_weak
        1 -> R.string.pm_strength_fair
        2 -> R.string.pm_strength_good
        else -> R.string.pm_strength_strong
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 4) {
            Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= level) pw.primary else pw.outlineVariant))
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(androidx.compose.ui.res.stringResource(label), color = pw.onVariant, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}

/** The top inset (status bar) as padding. */
@Composable
fun Modifier.statusBarPadding(): Modifier = windowInsetsPadding(WindowInsets.statusBars)

/** The bottom inset (navigation bar and gesture handle) as padding. */
@Composable
fun Modifier.navigationBarPadding(): Modifier = windowInsetsPadding(WindowInsets.navigationBars)

@Composable
fun SectionText(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = pw.onVariant, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp), overflow = TextOverflow.Clip,
    )
}
