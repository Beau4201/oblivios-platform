// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.text.DateFormat
import java.util.Date
import org.freeholdos.passwords.R
import org.freeholdos.passwords.core.Entry

private fun dateOf(ms: Long): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(ms))

@Composable
fun DetailScreen(c: AppController, idHex: String) {
    val e = c.current
    val scroll = rememberScrollState()
    var confirmDelete by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { } }
    Column(Modifier.fillMaxSize().background(if (scroll.value > 120) pw.bar else pw.bg)) {
        PwTopBar(if (scroll.value > 120 && e != null) e.title else null, onBack = { c.back() }) {
            IconAction(R.drawable.ic_edit, stringResource(R.string.pm_cd_edit), { c.editEntry() }, tint = pw.on)
        }
        if (e == null) return@Column
        Column(Modifier.weight(1f).background(pw.bg).verticalScroll(scroll)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoTile(e.title, 64.dp, logo = rememberLogo(e.domains, e.packages))
                Spacer(Modifier.height(12.dp))
                Text(e.title, color = pw.on, style = MaterialTheme.typography.titleLarge)
                (e.domains.firstOrNull() ?: e.packages.firstOrNull())?.let { Text(it, color = pw.onVariant, style = MaterialTheme.typography.bodyMedium) }
            }
            val others = c.checkup?.reused?.firstOrNull { g -> g.any { it.contentEquals(e.id) } }?.filterNot { it.contentEquals(e.id) }
                ?.mapNotNull { id -> c.entries.firstOrNull { it.id.contentEquals(id) }?.title }.orEmpty()
            if (others.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().background(pw.secondaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(16.dp)) {
                    PwIcon(R.drawable.ic_warning, tint = pw.error)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.pm_reused_title), color = pw.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.pm_reused_body, others.joinToString(", ")), color = pw.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                        PwTextButton(stringResource(R.string.pm_review_in_checkup), { c.top(Route.Checkup) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Group {
                item {
                    PrefRow(stringResource(R.string.pm_field_username), summary = null, trailing = { IconAction(R.drawable.ic_content_copy, stringResource(R.string.pm_cd_copy), { c.copy(c.activity.getString(R.string.pm_field_username), e.username, c.activity.getString(R.string.pm_copied_username)) }) })
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pm_field_password), color = pw.onVariant, style = MaterialTheme.typography.labelMedium)
                            Text(if (c.revealed) String(e.password) else "•".repeat(12), color = pw.on, style = MaterialTheme.typography.bodyLarge) // i18n-ok
                            StrengthBars(c.strengthOf(e.password), Modifier.padding(top = 8.dp))
                        }
                        IconAction(if (c.revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility, stringResource(if (c.revealed) R.string.pm_cd_hide else R.string.pm_cd_show), { c.reveal() })
                        IconAction(R.drawable.ic_content_copy, stringResource(R.string.pm_cd_copy), { c.copyPassword(e) })
                    }
                }
                e.domains.firstOrNull()?.let { site ->
                    item { PrefRow(stringResource(R.string.pm_field_website), summary = site, trailing = { IconAction(R.drawable.ic_open_in_new, stringResource(R.string.pm_cd_open_site), { c.openSite(site) }) }) }
                }
            }
            GroupHeader(stringResource(R.string.pm_detail_security))
            Group {
                item { PrefRow(stringResource(R.string.pm_changed), icon = R.drawable.ic_edit, summary = stringResource(R.string.pm_changed_value, dateOf(e.editedMs), c.lastFilledLabel(e.editedMs).orEmpty())) }
                if (e.history.isNotEmpty()) item {
                    PrefRow(
                        stringResource(R.string.pm_history), icon = R.drawable.ic_history,
                        summary = pluralStringResource(R.plurals.pm_history_count, e.history.size, e.history.size), onClick = { historyOpen = !historyOpen },
                        trailing = { PwIcon(R.drawable.ic_chevron_right) },
                    )
                }
                if (historyOpen) e.history.forEach { h ->
                    item { PrefRow(if (c.revealed) String(h.password) else "•".repeat(12), summary = dateOf(h.replacedMs)) } // i18n-ok
                }
            }
            GroupHeader(stringResource(R.string.pm_detail_autofill))
            Group {
                val fills = e.domains + e.packages
                if (fills.isNotEmpty()) item { PrefRow(stringResource(R.string.pm_fills_in), icon = R.drawable.ic_language, summary = fills.joinToString("\n")) }
                item { PrefRow(stringResource(R.string.pm_last_filled), icon = R.drawable.ic_schedule, summary = c.lastFilledLabel(e.lastFilledMs) ?: stringResource(R.string.pm_never_filled)) }
                item {
                    var offered by remember(idHex) { mutableStateOf(c.chipOffered(idHex)) }
                    PrefRow(stringResource(R.string.pm_offer_chip), icon = R.drawable.ic_keyboard, summary = stringResource(R.string.pm_offer_chip_sub), trailing = { PwSwitch(offered) { offered = it; c.setChipOffered(idHex, it) } })
                }
            }
            if (e.notes.isNotBlank()) {
                GroupHeader(stringResource(R.string.pm_detail_notes))
                Group { item { PrefRow(e.notes, icon = R.drawable.ic_notes) } }
            }
            SectionText(stringResource(R.string.pm_created_edited, dateOf(e.createdMs), dateOf(e.editedMs)), Modifier.padding(top = 16.dp))
            Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).navigationBarPadding()) {
                PwOutlinedButton(stringResource(R.string.pm_delete), { confirmDelete = true }, icon = R.drawable.ic_delete)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.pm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.pm_delete_confirm_body)) },
            confirmButton = { PwTextButton(stringResource(R.string.pm_delete_confirm_yes), { confirmDelete = false; c.delete(idHex) }) },
            dismissButton = { PwTextButton(stringResource(R.string.pm_cancel), { confirmDelete = false }) },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            containerColor = pw.row,
        )
    }
}

@Composable
fun EditScreen(c: AppController, idHex: String?) {
    val existing: Entry? = if (idHex != null) c.current else null
    val name = remember(idHex) { TextFieldState(existing?.title.orEmpty()) }
    val site = remember(idHex) { TextFieldState(existing?.domains?.firstOrNull().orEmpty()) }
    val user = remember(idHex) { TextFieldState(existing?.username.orEmpty()) }
    val pass = remember(idHex) { TextFieldState(existing?.password?.let { String(it) }.orEmpty()) }
    val notes = remember(idHex) { TextFieldState(existing?.notes.orEmpty()) }
    var show by remember { mutableStateOf(idHex == null) }
    var generated by remember { mutableStateOf(false) }
    val tooLong = stringResource(R.string.pm_edit_too_long)
    DisposableEffect(idHex) { onDispose { pass.clearText(); user.clearText(); notes.clearText() } }
    val strength = remember(pass.text) { val ch = pass.chars(); c.strengthOf(ch).also { ch.fill('\u0000') } }
    Column(Modifier.fillMaxSize().background(pw.bg)) {
        PwTopBar(stringResource(if (idHex == null) R.string.pm_new_password else R.string.pm_edit_title), onBack = { c.back() }, backIcon = R.drawable.ic_close) {
            PwButton(stringResource(R.string.pm_save), onClick = {
                val host = site.text.toString().trim().removePrefix("https://").removePrefix("http://").substringBefore('/').lowercase()
                val title = name.text.toString().trim().ifEmpty { host.removePrefix("www.") }
                val now = System.currentTimeMillis()
                val entry = Entry(
                    id = existing?.id ?: c.newId(), title = title, domains = if (host.isEmpty()) emptyList() else listOf(host),
                    packages = existing?.packages.orEmpty(), username = user.text.toString().trim(), password = pass.chars(), notes = notes.text.toString(),
                    createdMs = existing?.createdMs ?: now, editedMs = now, lastFilledMs = existing?.lastFilledMs, history = existing?.history.orEmpty(),
                )
                if (c.save(entry)) c.back()
            }, enabled = pass.text.isNotEmpty() && (site.text.isNotBlank() || name.text.isNotBlank() || user.text.isNotBlank()))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                LogoTile(name.text.toString().ifBlank { site.text.toString() }.ifBlank { "?" }, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(name.text.toString().ifBlank { site.text.toString() }, color = pw.on, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pm_logo_builtin), color = pw.onVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            PwField(name, stringResource(R.string.pm_field_name))
            Spacer(Modifier.height(12.dp))
            PwField(site, stringResource(R.string.pm_field_website), keyboardType = KeyboardType.Uri)
            Spacer(Modifier.height(12.dp))
            PwField(user, stringResource(R.string.pm_field_username), keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            PwSecretField(
                pass, stringResource(R.string.pm_field_password), visible = show,
                trailing = {
                    Row {
                        // Hiding is free. Showing the stored password of an existing entry asks again, as the eye of the detail screen does;
                        // a new entry, or a password that was just generated, has nothing stored to protect.
                        IconAction(if (show) R.drawable.ic_visibility_off else R.drawable.ic_visibility, stringResource(if (show) R.string.pm_cd_hide else R.string.pm_cd_show), {
                            if (show || idHex == null || c.revealed || generated) show = !show else c.confirmThen { show = true }
                        })
                        IconAction(R.drawable.ic_refresh, stringResource(R.string.pm_cd_generate), {
                            val g = c.generatePassword()
                            pass.setTextAndPlaceCursorAtEnd(String(g))
                            g.fill('\u0000')
                            generated = true
                            show = true
                        })
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            StrengthBars(strength)
            Text(stringResource(R.string.pm_generated_here), color = pw.onVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, start = 16.dp))
            Spacer(Modifier.height(20.dp))
            PwField(notes, stringResource(R.string.pm_field_notes), singleLine = false, supporting = stringResource(R.string.pm_optional))
            Spacer(Modifier.height(32.dp).navigationBarPadding())
        }
    }
}
