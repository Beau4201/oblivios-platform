// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import org.freeholdos.passwords.R
import org.freeholdos.passwords.core.WeakReason

@Composable
fun CheckupScreen(c: AppController) {
    val cu = c.checkup
    val count = c.attentionCount()
    Column(Modifier.fillMaxSize().background(pw.bg)) {
        PwTopBar(stringResource(R.string.pm_nav_checkup), onBack = null)
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PwIcon(R.drawable.ic_verified_user_filled, tint = pw.primary, size = 48.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (count > 0) pluralStringResource(R.plurals.pm_checkup_summary, count, count) else stringResource(R.string.pm_checkup_none),
                        color = pw.on, style = MaterialTheme.typography.titleLarge,
                    )
                    Text(stringResource(R.string.pm_checkup_offline), color = pw.onVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (cu != null && cu.reused.isNotEmpty()) {
                item { GroupHeader(stringResource(R.string.pm_checkup_reused)) }
                cu.reused.forEach { group ->
                    val titles = group.map { id -> c.entries.firstOrNull { it.id.contentEquals(id) }?.title.orEmpty() }
                    items(group.size) { i ->
                        val s = c.entries.firstOrNull { it.id.contentEquals(group[i]) } ?: return@items
                        val others = titles.filterIndexed { j, _ -> j != i }.joinToString(", ")
                        EntryRow(s.title, stringResource(R.string.pm_checkup_reused_with, others), false, { c.openEntry(hexOf(s.id)) }, chevron = true, domains = s.domains, packages = s.packages)
                    }
                }
            }
            if (cu != null && cu.weak.isNotEmpty()) {
                item { GroupHeader(stringResource(R.string.pm_checkup_weak)) }
                items(cu.weak.size) { i ->
                    val (id, reason) = cu.weak[i]
                    val s = c.entries.firstOrNull { it.id.contentEquals(id) } ?: return@items
                    val text = when (reason) {
                        WeakReason.TOO_SHORT -> R.string.pm_checkup_weak_short
                        WeakReason.COMMON -> R.string.pm_checkup_weak_common
                        WeakReason.PATTERN -> R.string.pm_checkup_weak_pattern
                    }
                    EntryRow(s.title, stringResource(text), false, { c.openEntry(hexOf(s.id)) }, chevron = true, domains = s.domains, packages = s.packages)
                }
            }
            item { SectionText(stringResource(R.string.pm_checkup_no_breach), Modifier.padding(top = 16.dp, start = 0.dp)) }
        }
        PwNavBar(1) { navigate(c, it) }
    }
}

@Composable
fun SettingsScreen(c: AppController) {
    var biometrics by remember { mutableStateOf(c.hasBiometrics()) }
    var suggestions by remember { mutableStateOf(c.suggestionsOn) }
    var active by remember { mutableStateOf(c.autofillServiceActive()) }
    val changes = c.changesSinceExport()
    Column(Modifier.fillMaxSize().background(pw.bg)) {
        PwTopBar(stringResource(R.string.pm_settings_title), onBack = null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GroupHeader(stringResource(R.string.pm_settings_security))
            Group {
                item {
                    PrefRow(stringResource(R.string.pm_settings_biometrics), summary = stringResource(R.string.pm_settings_biometrics_sub), trailing = {
                        PwSwitch(biometrics) { on -> c.toggleBiometrics(on) { now -> biometrics = now } }
                    })
                }
                item { PrefRow(stringResource(R.string.pm_settings_autolock), summary = stringResource(R.string.pm_settings_autolock_sub)) }
            }
            GroupHeader(stringResource(R.string.pm_settings_autofill))
            Group {
                item {
                    PrefRow(
                        stringResource(R.string.pm_settings_service),
                        summary = stringResource(if (active) R.string.pm_settings_service_active else R.string.pm_settings_service_inactive),
                        onClick = { c.requestAutofillService(); active = c.autofillServiceActive() },
                    )
                }
                item {
                    PrefRow(stringResource(R.string.pm_settings_suggestions), summary = stringResource(R.string.pm_settings_suggestions_sub), trailing = {
                        PwSwitch(suggestions) { on -> c.suggestionsOn = on; suggestions = on }
                    })
                }
            }
            GroupHeader(stringResource(R.string.pm_settings_browsers))
            var browsers by remember { mutableStateOf(c.browserRows()) }
            Group {
                item { PrefRow(stringResource(R.string.pm_settings_browsers_vanadium), summary = stringResource(R.string.pm_settings_browsers_vanadium_sub)) }
                browsers.forEach { b ->
                    item {
                        PrefRow(b.label, summary = b.pkg, trailing = {
                            PwSwitch(b.trusted) { on -> c.setBrowserTrusted(b.pkg, on) { browsers = c.browserRows() } }
                        })
                    }
                }
            }
            SectionText(stringResource(if (browsers.isEmpty()) R.string.pm_settings_browsers_none else R.string.pm_settings_browsers_note))
            GroupHeader(stringResource(R.string.pm_settings_data))
            Group {
                item {
                    PrefRow(
                        stringResource(R.string.pm_settings_export),
                        summary = if (c.lastExportMs == 0L) stringResource(R.string.pm_settings_export_never) else pluralStringResource(R.plurals.pm_settings_export_sub, changes, changes),
                        onClick = { c.go(Route.Export) },
                    )
                }
                item { PrefRow(stringResource(R.string.pm_settings_import), onClick = { c.pickImportSource?.invoke() }) }
            }
        }
        PwNavBar(2) { navigate(c, it) }
    }
}

@Composable
fun ExportScreen(c: AppController) {
    val pass = remember { TextFieldState() }
    var show by remember { mutableStateOf(false) }
    var short by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { pass.clearText() } }
    Column(Modifier.fillMaxSize().background(pw.bg)) {
        PwTopBar(null, onBack = { c.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            PwIcon(R.drawable.ic_upload_file, tint = pw.primary, size = 40.dp)
            Spacer(Modifier.height(16.dp))
            Title(stringResource(R.string.pm_export_title))
            Spacer(Modifier.height(16.dp))
            Body(pluralStringResource(R.plurals.pm_export_body_1, c.entries.size, c.entries.size))
            Spacer(Modifier.height(8.dp))
            Body(stringResource(R.string.pm_export_body_2))
            Spacer(Modifier.height(24.dp))
            PwSecretField(
                pass, stringResource(R.string.pm_export_passphrase), visible = show, isError = short,
                supporting = if (short) stringResource(R.string.pm_export_passphrase_short) else stringResource(R.string.pm_export_warning),
                trailing = { IconAction(if (show) R.drawable.ic_visibility_off else R.drawable.ic_visibility, stringResource(if (show) R.string.pm_cd_hide else R.string.pm_cd_show), { show = !show }) },
            )
        }
        Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarPadding(), horizontalArrangement = Arrangement.End) {
            PwTextButton(stringResource(R.string.pm_cancel), { c.back() })
            Spacer(Modifier.padding(4.dp))
            PwButton(stringResource(R.string.pm_export), onClick = {
                val chars = pass.chars()
                short = chars.size < 8
                if (!short) c.startExport(chars) else chars.fill('\u0000')
            })
        }
    }
}
