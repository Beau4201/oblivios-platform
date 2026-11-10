// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.fill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.autofill.InlinePresentation
import android.view.inputmethod.InlineSuggestionsRequest
import androidx.autofill.inline.v1.InlineSuggestionUi
import org.oblivios.passwords.MainActivity
import org.oblivios.passwords.R

/**
 * The content of a chip (ADR-191): a key icon and one line of text, the username or the generic text. The look (colour,
 * size, shape) is the style of the keyboard in its request; the system draws the chip in another process. A request
 * from a keyboard that [ChipPolicy] does not trust gives no instance, and the system list is used.
 */
class InlineChips private constructor(private val context: Context, private val request: InlineSuggestionsRequest) {
    /** The most chips the keyboard wants. */
    val maxCount: Int get() = request.maxSuggestionCount

    /** The chip for the dataset at [datasetIndex]; a null [label] is the generic text of a locked vault. */
    fun presentation(label: String?, datasetIndex: Int): InlinePresentation {
        val specs = request.inlinePresentationSpecs
        val spec = specs[ChipPolicy.specIndex(datasetIndex, specs.size)]
        // The system asks for this intent on a long press of the chip. It opens the app, which shows the locked screen
        // when the vault is locked; it carries nothing.
        val attribution = PendingIntent.getActivity(context, ATTRIBUTION_CODE, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(label ?: context.getString(R.string.pm_chip_generic))
            .setStartIcon(Icon.createWithResource(context, R.drawable.ic_key))
            .build()
        return InlinePresentation(content.slice, spec, false)
    }

    companion object {
        private const val ATTRIBUTION_CODE = 2

        fun of(context: Context, request: InlineSuggestionsRequest?): InlineChips? {
            if (request == null) return null
            val allowed = ChipPolicy.inlineAllowed(request.hostPackageName, request.inlinePresentationSpecs.size, request.maxSuggestionCount)
            return if (allowed) InlineChips(context, request) else null
        }
    }
}
