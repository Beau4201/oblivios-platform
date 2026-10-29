// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.layout.systemBarsPadding
import org.freeholdos.passwords.R
import org.freeholdos.passwords.core.Summary

private fun subtitleOf(s: Summary) = s.username.ifEmpty { s.domains.firstOrNull() ?: s.packages.firstOrNull() ?: "" }

@Composable
fun PasswordsScreen(c: AppController) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(pw.bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarPadding().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(pw.bar)
                    .clickable { c.go(Route.Search) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PwIcon(R.drawable.ic_search)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.pm_search_hint), color = pw.onVariant, style = MaterialTheme.typography.bodyLarge)
            }
            val attention = c.attentionCount()
            if (attention > 0) CheckupCard(attention) { c.top(Route.Checkup) }
            if (c.entries.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(48.dp))
                    Title(stringResource(R.string.pm_empty_title))
                    Spacer(Modifier.height(8.dp))
                    Body(stringResource(R.string.pm_empty_body))
                }
            } else {
                LazyColumn(Modifier.weight(1f), state = listState) {
                    items(c.entries, key = { hexOf(it.id) }) { s ->
                        EntryRow(
                            s.title, subtitleOf(s), c.needsAttention(s.id), { c.openEntry(hexOf(s.id)) },
                            domains = s.domains, packages = s.packages, modifier = Modifier.animateItem(),
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
            PwNavBarSpace()
        }
        // The button folds to its icon while the list is scrolled and opens again at the top, the way the system apps do.
        ExtendedFloatingActionButton(
            onClick = { c.newEntry() }, expanded = !listState.canScrollBackward,
            shape = RoundedCornerShape(16.dp), containerColor = pw.secondaryContainer, contentColor = pw.onSecondaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarPadding().padding(end = 16.dp, bottom = 80.dp + 16.dp),
            icon = { PwIcon(R.drawable.ic_add, tint = pw.onSecondaryContainer) },
            text = { Text(stringResource(R.string.pm_new_password)) },
        )
    }
}

fun navigate(c: AppController, index: Int) = c.top(when (index) { 0 -> Route.Passwords; 1 -> Route.Checkup; else -> Route.Settings })

@Composable
private fun CheckupCard(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp)).background(pw.secondaryContainer).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PwIcon(R.drawable.ic_verified_user_filled, tint = pw.onSecondaryContainer)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(pluralStringResource(R.plurals.pm_checkup_card_title, count, count), color = pw.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pm_checkup_card_sub), color = pw.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
        }
        PwIcon(R.drawable.ic_chevron_right, tint = pw.onSecondaryContainer)
    }
}

@Composable
fun SearchScreen(c: AppController) {
    val query = remember { TextFieldState() }
    val focus = remember { FocusRequester() }
    var results by remember { mutableStateOf<List<Summary>>(emptyList()) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        snapshotFlow { query.text.toString() }.collect { q -> results = if (q.isBlank()) emptyList() else c.search(q).orEmpty() }
    }
    Column(Modifier.fillMaxSize().background(pw.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarPadding().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(pw.bar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(R.drawable.ic_arrow_back, stringResource(R.string.pm_cd_back), { c.back() }, tint = pw.on)
            OutlinedTextField(
                state = query, modifier = Modifier.weight(1f).focusRequester(focus), lineLimits = TextFieldLineLimits.SingleLine,
                placeholder = { Text(stringResource(R.string.pm_search_hint)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = pw.on, unfocusedTextColor = pw.on, cursorColor = pw.primary,
                ),
            )
            if (query.text.isNotEmpty()) IconAction(R.drawable.ic_close, stringResource(R.string.pm_cd_clear), { query.clearText() }, tint = pw.onVariant)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { hexOf(it.id) }) { s ->
                EntryRow(s.title, subtitleOf(s), c.needsAttention(s.id), { c.openEntry(hexOf(s.id)) }, modifier = Modifier.animateItem())
            }
            if (query.text.isNotBlank()) {
                // 16 dp: the plain list here has its logos at 16 dp, not inside a card.
                item { SectionText(pluralStringResource(R.plurals.pm_search_results, results.size, results.size), Modifier.padding(top = 8.dp), inset = 16.dp) }
            }
        }
    }
}
