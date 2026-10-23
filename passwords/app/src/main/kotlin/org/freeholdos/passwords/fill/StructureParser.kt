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
    fun parse(structure: AssistStructure, policy: OriginPolicy): ParsedScreen {
        val fields = ArrayList<ParsedField>()
        val ids = ArrayList<AutofillId>()
        val values = ArrayList<String?>()
        val domains = LinkedHashSet<String>()
        var plainHttp = false
        val pkg = structure.activityComponent?.packageName ?: ""
        for (w in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(w).rootViewNode) { node ->
            node.webDomain?.takeIf { it.isNotEmpty() }?.let { d ->
                domains.add(d)
                if ("http".equals(node.webScheme, ignoreCase = true)) plainHttp = true
            }
            val id = node.autofillId ?: return@walk
            if (node.autofillType != android.view.View.AUTOFILL_TYPE_TEXT) return@walk
            // A field with no size cannot be seen or typed into: a page script that adds one only wants what the fill puts there.
            if (node.width <= 0 || node.height <= 0) return@walk
            val role = classify(node.autofillHints?.toList() ?: emptyList(), node.inputType, node.idEntry)
            if (role == FieldRole.Other) return@walk
            fields.add(ParsedField(ids.size.toString(), role))
            ids.add(id)
            values.add(node.autofillValue?.takeIf(AutofillValue::isText)?.textValue?.toString())
        }
        val nothing = ParsedScreen(emptyList(), emptyList(), emptyList(), null, pkg)
        // Review of 2026-10-23, P-02 and P-03: a trusted browser's screen that reports more than one site (a frame of
        // another origin) or a plain http page is not offered a fill and is not offered a save; the site of the first node
        // used to be applied to every field, and https credentials were filled on http pages.
        if (policy.trusted(pkg) && (domains.size > 1 || plainHttp)) return nothing
        // P-01 and P-04: the site is believed from a trusted browser only; an app that shows no website is matched by its
        // package; a browser that is not trusted, and an app that shows web content, get nothing.
        return when (val origin = policy.resolve(pkg, domains.firstOrNull())) {
            Origin.None -> nothing
            is Origin.Site -> ParsedScreen(fields, ids, values, origin.domain, pkg)
            is Origin.App -> ParsedScreen(fields, ids, values, null, pkg)
        }
    }

    /** Visits [node] and the visible nodes below it; a node that is not visible hides its whole subtree (P-03). */
    private fun walk(node: AssistStructure.ViewNode, visit: (AssistStructure.ViewNode) -> Unit) {
        if (node.visibility != android.view.View.VISIBLE) return
        visit(node)
        for (i in 0 until node.childCount) walk(node.getChildAt(i), visit)
    }
}
