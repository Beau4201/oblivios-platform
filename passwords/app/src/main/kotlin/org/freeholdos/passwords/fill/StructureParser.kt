// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.app.assist.AssistStructure
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue

/** What the service reads from one screen: the login fields, where the screen comes from, and what the user typed. */
class ParsedScreen(
    val fields: List<ParsedField>,
    val ids: List<AutofillId>,
    val values: List<String?>,
    val webDomain: String?,
    val packageName: String,
) {
    /** The AutofillIds of the fields with the given keys, in the order of [keys]. */
    fun idsOf(keys: List<String>): List<AutofillId> = keys.mapNotNull { k -> k.toIntOrNull()?.let(ids::getOrNull) }
}

/** Walks an AssistStructure once. The key of a field is its index in [ParsedScreen.ids]. */
object StructureParser {
    fun parse(structure: AssistStructure): ParsedScreen {
        val fields = ArrayList<ParsedField>()
        val ids = ArrayList<AutofillId>()
        val values = ArrayList<String?>()
        var webDomain: String? = null
        val pkg = structure.activityComponent?.packageName ?: ""
        for (w in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(w).rootViewNode) { node ->
            if (webDomain == null) webDomain = node.webDomain?.takeIf { it.isNotEmpty() }
            val id = node.autofillId ?: return@walk
            if (node.autofillType != android.view.View.AUTOFILL_TYPE_TEXT) return@walk
            val role = classify(node.autofillHints?.toList() ?: emptyList(), node.inputType, node.idEntry)
            if (role == FieldRole.Other) return@walk
            fields.add(ParsedField(ids.size.toString(), role))
            ids.add(id)
            values.add(node.autofillValue?.takeIf(AutofillValue::isText)?.textValue?.toString())
        }
        return ParsedScreen(fields, ids, values, webDomain, pkg)
    }

    private fun walk(node: AssistStructure.ViewNode, visit: (AssistStructure.ViewNode) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) walk(node.getChildAt(i), visit)
    }
}
