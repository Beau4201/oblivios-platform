// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.freeholdos.passwords.R

@Composable
fun PwButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null, enabled: Boolean = true) {
    Button(
        onClick, modifier.height(40.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = pw.primary, contentColor = pw.onPrimary),
    ) {
        if (icon != null) {
            PwIcon(icon, tint = pw.onPrimary, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
fun PwTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick, modifier, colors = ButtonDefaults.textButtonColors(contentColor = pw.primary)) { Text(text) }
}

@Composable
fun PwOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null, color: androidx.compose.ui.graphics.Color = pw.error) {
    OutlinedButton(onClick, modifier.height(40.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
        if (icon != null) {
            PwIcon(icon, tint = color, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

private val FieldShape = RoundedCornerShape(4.dp)

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedIndicatorColor = pw.primary, unfocusedIndicatorColor = pw.outline,
    focusedLabelColor = pw.primary, unfocusedLabelColor = pw.onVariant,
    focusedTextColor = pw.on, unfocusedTextColor = pw.on, cursorColor = pw.primary,
)

/** An outlined text field, 56 dp, 4 dp corners (reference page). */
@Composable
fun PwField(
    state: TextFieldState, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true, trailing: (@Composable () -> Unit)? = null, supporting: String? = null, isError: Boolean = false,
) {
    OutlinedTextField(
        state = state, modifier = modifier.fillMaxWidth(), label = { Text(label) }, shape = FieldShape, lineLimits = if (singleLine) androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine else androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(minHeightInLines = 3),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        trailingIcon = trailing, supportingText = supporting?.let { { Text(it) } }, isError = isError, colors = fieldColors(),
    )
}

/** A secret field: the text is held in a [TextFieldState] that the caller clears, and is hidden unless [visible]. */
@Composable
fun PwSecretField(
    state: TextFieldState, label: String, visible: Boolean, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null, supporting: String? = null, isError: Boolean = false,
) {
    OutlinedSecureTextField(
        state = state, modifier = modifier.fillMaxWidth(), label = { Text(label) }, shape = FieldShape,
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        trailingIcon = trailing, supportingText = supporting?.let { { Text(it) } }, isError = isError, colors = fieldColors(),
    )
}

/** The text of a field as a character array that the caller wipes. */
fun TextFieldState.chars(): CharArray = text.let { t -> CharArray(t.length) { t[it] } }

/** Two-line list row: the logo, the title, the username, an optional mark and chevron. */
@Composable
fun EntryRow(
    name: String, subtitle: String, attention: Boolean, onClick: () -> Unit, chevron: Boolean = false,
    domains: List<String> = emptyList(), packages: List<String> = emptyList(),
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoTile(name, 40.dp, logo = rememberLogo(domains, packages))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = pw.on, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, color = pw.onVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (attention) {
            PwIcon(R.drawable.ic_warning, tint = pw.error, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (chevron) PwIcon(R.drawable.ic_chevron_right)
    }
}

/** The bottom navigation bar: 80 dp plus the gesture inset, the selected destination with the filled icon in a 64 x 32 pill. */
@Composable
fun PwNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple(R.drawable.ic_key, R.drawable.ic_key_filled, R.string.pm_nav_passwords),
        Triple(R.drawable.ic_verified_user, R.drawable.ic_verified_user_filled, R.string.pm_nav_checkup),
        Triple(R.drawable.ic_settings, R.drawable.ic_settings_filled, R.string.pm_nav_settings),
    )
    Column(Modifier.fillMaxWidth().background(pw.bar).navigationBarPadding()) {
        Row(Modifier.fillMaxWidth().height(80.dp)) {
            items.forEachIndexed { i, (outline, filled, label) ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).height(80.dp).clickable(role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.width(64.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) pw.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
                        PwIcon(if (on) filled else outline, tint = if (on) pw.onSecondaryContainer else pw.onVariant)
                    }
                    Text(stringResource(label), color = if (on) pw.on else pw.onVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** A bar with a title and up to two actions. */
@Composable
fun PwTopBar(title: String?, onBack: (() -> Unit)?, backIcon: Int = R.drawable.ic_arrow_back, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconAction(backIcon, stringResource(if (backIcon == R.drawable.ic_close) R.string.pm_cd_close else R.string.pm_cd_back), onBack, tint = pw.on)
        Text(title ?: "", color = pw.on, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = if (onBack == null) 20.dp else 4.dp), maxLines = 1)
        actions()
    }
}

/** The message at the bottom, e.g. "Password copied". */
@Composable
fun Toast(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text, color = pw.onInverse, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(pw.inverse).padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun Centered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
}

@Composable
fun Title(text: String) = Text(text, color = pw.on, style = MaterialTheme.typography.headlineLarge)

@Composable
fun Body(text: String, modifier: Modifier = Modifier) = Text(text, color = pw.onVariant, style = MaterialTheme.typography.bodyLarge, modifier = modifier)

@Composable
fun Spacer16() = Spacer(Modifier.size(16.dp))
