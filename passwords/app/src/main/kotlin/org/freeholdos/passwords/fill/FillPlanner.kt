// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import org.freeholdos.passwords.lock.LockState

/** A field of the screen being filled; [key] is the caller's handle for it (an AutofillId in the service). */
class ParsedField(val key: String, val role: FieldRole)

/** An account that fits the site or app; the username is all that is ever shown. */
class Account(val entryId: ByteArray, val username: String)

/** The answers the planner needs. While the vault is locked only [hasAccount] may be asked. */
interface SiteLookup {
    /** From the domain index: is there an account for this site (a host) or, with no host, this app? Works while locked. */
    fun hasAccount(webDomain: String?, packageName: String): Boolean

    /** The accounts; needs the open vault. */
    fun accounts(webDomain: String?, packageName: String): List<Account>
}

class DatasetPlan(val entryId: ByteArray, val label: String?, val usernameKeys: List<String>, val passwordKeys: List<String>) {
    /** Never holds a password: the password is fetched by [entryId] when the dataset is built. */
    override fun toString() = "DatasetPlan(label=$label, ${usernameKeys.size} username field(s), ${passwordKeys.size} password field(s))"
}

sealed interface FillPlan {
    /** Nothing to offer: no login field, or no account for this site or app. */
    data object None : FillPlan

    /** The vault is locked and there is an account: one generic entry that asks for authentication, then fills. */
    data class Authenticate(val fieldKeys: List<String>) : FillPlan

    /** The vault is open: one dataset per account, labelled with the username only. */
    data class Datasets(val items: List<DatasetPlan>) : FillPlan
}

/**
 * What to put above the keyboard (or in the dropdown) for a screen. A host is the identity of a site; only when there is
 * none, in a native app, the package name is. A locked vault shows the same single entry whatever the number of accounts.
 */
fun plan(fields: List<ParsedField>, webDomain: String?, packageName: String, state: LockState, lookup: SiteLookup): FillPlan {
    val users = fields.filter { it.role == FieldRole.Username }.map { it.key }
    val passwords = fields.filter { it.role == FieldRole.Password }.map { it.key }
    if (users.isEmpty() && passwords.isEmpty()) return FillPlan.None
    if (state !is LockState.Unlocked) {
        return if (lookup.hasAccount(webDomain, packageName)) FillPlan.Authenticate(fields.filter { it.role != FieldRole.Other }.map { it.key }) else FillPlan.None
    }
    val accounts = lookup.accounts(webDomain, packageName)
    if (accounts.isEmpty()) return FillPlan.None
    return FillPlan.Datasets(accounts.map { DatasetPlan(it.entryId, it.username.ifEmpty { null }, users, passwords) })
}

/**
 * The login a user just submitted on a form we did not fill (or filled differently). The password is a character array
 * that [wipe] clears; nothing prints it.
 */
class SaveCandidate private constructor(val username: String, val password: CharArray) {
    fun wipe() = password.fill('\u0000')

    override fun toString() = "SaveCandidate(username ${username.length} chars, password hidden)"

    companion object {
        /** The last non-empty password and the first username of the submitted values, or null when there is no password. */
        fun of(values: List<Pair<FieldRole, String>>): SaveCandidate? {
            val password = values.lastOrNull { it.first == FieldRole.Password && it.second.isNotEmpty() }?.second ?: return null
            val username = values.firstOrNull { it.first == FieldRole.Username }?.second ?: ""
            return SaveCandidate(username, password.toCharArray())
        }
    }
}
