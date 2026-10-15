// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.guardianui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.sovereignos.guardian.R

/**
 * The real FreeholdOS shield mark — a transparent-background crop of the
 * actual, ADR-019-approved boot asset. Never replace this with a
 * hand-drawn vector; two attempts at that were both visibly wrong in
 * review (see the visual-identity design spec, "The mark").
 *
 * FreeholdOS Android port deviation (see guardian/SOURCE.md): the reference
 * module decodes the PNG with `androidx.compose.ui.res.useResource` and
 * `org.jetbrains.skia.Image`, both Compose Desktop-only APIs that do not
 * exist on Android. The same bytes ship here as the `guardian_mark`
 * drawable resource and are loaded with Android's own `painterResource`.
 * The asset itself is byte-identical to the reference module's
 * `src/main/resources/guardian_mark.png`.
 */
@Composable
fun GuardianMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.guardian_mark),
        contentDescription = "FreeholdOS shield mark",
        modifier = modifier.aspectRatio(1f),
    )
}
