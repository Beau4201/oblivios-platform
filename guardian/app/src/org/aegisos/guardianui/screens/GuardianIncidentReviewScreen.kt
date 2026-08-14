package org.aegisos.guardianui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.aegisos.guardianui.components.GuardianFloatingCard
import org.aegisos.guardianui.components.GuardianPrimaryButton
import org.aegisos.guardianui.components.GuardianStatusChip
import org.aegisos.guardianui.components.guardianScreenTint
import org.aegisos.guardianui.icons.GuardianIcons
import org.aegisos.guardianui.theme.GuardianColor

/**
 * AegisOS Android port deviation (see guardian/SOURCE.md). The real app
 * resolves the whole Observe -> Explain -> Choose -> Enforce -> Verify ->
 * Undo loop on this one screen (the design spec asks for exactly that), so
 * the status chip has to be able to say "protected" or "this could not be
 * verified", not only "unusual". Defaults to [Unusual], which is the
 * reference module's only state.
 */
enum class GuardianIncidentTone { Unusual, Protected, Attention }

data class GuardianIncidentUiState(
    val appName: String,
    val permissionLabel: String,
    val unusualReason: String,
    val observed: String,
    val firstSeen: String,
    val ifRestricted: String,
    val appIconContent: @Composable () -> Unit,
    val tone: GuardianIncidentTone = GuardianIncidentTone.Unusual,
)

/**
 * [primaryActionLabel] and [secondaryActionLabel] are an AegisOS Android
 * port deviation (see guardian/SOURCE.md), for the same reason as
 * [GuardianIncidentTone]: once the owner has acted, this screen's two
 * actions become "Done" plus "Undo"/"Try again" rather than
 * "Restrict ..."/"Not now". The defaults reproduce the reference module's
 * labels exactly; a null [secondaryActionLabel] hides the secondary action.
 *
 * [secondaryActionNote] is a further AegisOS deviation: a caveat rendered
 * directly under the secondary action, for the case where accepting that
 * action promises the owner less than its label implies. It exists because
 * deferring a detection is only as durable as the in-memory queue holding it.
 * Defaults to null, so the reference module's own call sites render exactly
 * as before.
 */
@Composable
fun GuardianIncidentReviewScreen(
    state: GuardianIncidentUiState,
    onBack: () -> Unit,
    onRestrict: () -> Unit,
    onNotNow: () -> Unit,
    primaryActionLabel: String = "Restrict ${state.permissionLabel.replaceFirstChar { it.lowercase() }}",
    secondaryActionLabel: String? = "Not now",
    secondaryActionNote: String? = null,
) {
    Column(modifier = Modifier.fillMaxSize().background(GuardianColor.Surface).guardianScreenTint(GuardianColor.Glow, 0.16f)) {
        // .m3-topbar (back-arrow variant): icon-btn is the same 28x28
        // circular touch target as Home's gear, not a 48dp minimum inflating
        // the row; title override is 18px/500 here, not the shared
        // titleMedium (16sp).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // icon-btn has no background fill in the design (no clip needed
            // visually) — the 28dp box is layout only. The real touch target
            // still needs to be >=48dp; enforced on this same Box rather than
            // inflating the visible icon, same approach as the settings gear.
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics(mergeDescendants = true) {}
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GuardianIcons.ArrowBackIos,
                    contentDescription = "Back",
                    tint = GuardianColor.OnSurface,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Review access",
                style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                color = GuardianColor.OnSurface,
            )
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // .m3-app-row: padding 4px 4px 8px; icon 48x48, radius 16, inner
            // glyph 26x26; name 16.5px/600; perm 11.5px. Was 44dp/radius 12
            // with shared titleMedium/bodyMedium text and no row padding.
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    state.appIconContent()
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        state.appName,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold),
                        color = GuardianColor.OnSurface,
                    )
                    Text(
                        state.permissionLabel,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 11.5.sp),
                        color = GuardianColor.OnSurfaceVariant,
                    )
                }
            }

            when (state.tone) {
                GuardianIncidentTone.Unusual -> GuardianStatusChip(
                    icon = GuardianIcons.Warning,
                    label = state.unusualReason,
                    containerColor = GuardianColor.WarningContainer,
                    contentColor = GuardianColor.OnWarningContainer,
                )
                GuardianIncidentTone.Protected -> GuardianStatusChip(
                    icon = GuardianIcons.Check,
                    label = state.unusualReason,
                    containerColor = GuardianColor.SuccessContainer,
                    contentColor = GuardianColor.OnSuccessContainer,
                )
                GuardianIncidentTone.Attention -> GuardianStatusChip(
                    icon = GuardianIcons.Warning,
                    label = state.unusualReason,
                    containerColor = GuardianColor.ErrorContainer,
                    contentColor = GuardianColor.OnErrorContainer,
                )
            }

            // .m3-facts { gap: 14px } between fact rows — was a bare Column
            // with no spacing between FactRow calls.
            GuardianFloatingCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FactRow("Observed", state.observed)
                    FactRow("First seen", state.firstSeen)
                    FactRow("If restricted", state.ifRestricted)
                    FactRow("Undo", "Always available")
                }
            }

            Spacer(Modifier.weight(1f))

            GuardianPrimaryButton(
                text = primaryActionLabel,
                onClick = onRestrict,
            )
            secondaryActionLabel?.let { label ->
                // AegisOS deviation: the label and its caveat are one visual
                // unit, so they are wrapped rather than left as two children
                // of the spaced parent. With no note (the reference module's
                // only case) the layout is unchanged.
                //
                // They are one *semantic* unit too: the click and the button
                // role sit on this wrapper, so both texts merge into a single
                // node and a screen reader announces the caveat together with
                // the button it qualifies, instead of leaving it as a separate
                // element the owner reaches only after deciding. The clickable
                // has to be here rather than on the label for that to work at
                // all — Compose skips a merging descendant when it merges
                // (SemanticsNode.mergeConfig), and `clickable` is one, so
                // marking this Column mergeDescendants while the label stayed
                // clickable would have merged the note *without* the label.
                // With no note the merged node keeps the same bounds, the same
                // touch target and the same announcement as before.
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onNotNow),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = GuardianColor.Primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(10.dp),
                    )
                    secondaryActionNote?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GuardianColor.OnSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FactRow(key: String, value: String) {
    // .m3-fact: gap 12px, font-size 12.5px; .k plain, .v weight 500,
    // text-align right. The value (not the key) can be long, so it — not
    // the key — carries the weight: an unweighted value Text claimed the
    // row's full measurement pass for a short sentence, squeezing the
    // weighted key Text down to a few pixels, wrapping it one character
    // per line. Confirmed live on a real Android build of this exact
    // FactRow (this file's deviation #8 in guardian/SOURCE.md) before being
    // traced back to and fixed at its shared origin in the reference module.
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(key, style = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp), color = GuardianColor.OnSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = GuardianColor.OnSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
