// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

enum class FieldRole { Username, Password, Other }

/** The values of android.text.InputType that matter here, copied so that this file stays free of Android classes. */
object InputTypes {
    const val CLASS_MASK = 0xf
    const val VARIATION_MASK = 0xff0
    const val CLASS_TEXT = 0x1
    const val CLASS_NUMBER = 0x2

    const val TEXT = CLASS_TEXT
    const val NUMBER = CLASS_NUMBER
    const val TEXT_EMAIL = CLASS_TEXT or 0x20
    const val TEXT_PASSWORD = CLASS_TEXT or 0x80
    const val TEXT_VISIBLE_PASSWORD = CLASS_TEXT or 0x90
    const val TEXT_WEB_EMAIL = CLASS_TEXT or 0xd0
    const val TEXT_WEB_PASSWORD = CLASS_TEXT or 0xe0
    const val NUMBER_PASSWORD = CLASS_NUMBER or 0x10
}

// Android's hint names, and the `autocomplete` tokens of a web page, which a browser passes on as they are: Vanadium sends
// `username` and `current-password` [measured: dump of a fill request on lynx 2026102302, 2026-10-28, PWM-004].
private val USERNAME_HINTS = setOf("username", "emailaddress", "email")
private val PASSWORD_HINTS = setOf("password", "newpassword", "current-password", "new-password")

private val USER_TOKENS = setOf("user", "username", "userid", "uname", "login", "loginid", "email", "mail", "emailaddress", "account", "accountid", "uid")
private val PASSWORD_TOKENS = setOf("pass", "password", "passwd", "pwd", "passphrase", "passcode")

/** Words in a field id that say "this is not a login field", whatever else the id says. */
private val NOT_LOGIN_TOKENS = setOf(
    "search", "query", "q", "find", "filter", "coupon", "promo", "zip", "postal", "phone", "tel", "mobile",
    "otp", "captcha", "address", "city", "country", "comment", "message", "title", "subject",
)

/** `etUserName`, `login_email`, `account-id` -> lower-case words. */
private fun tokens(id: String?): Set<String> {
    if (id.isNullOrEmpty()) return emptySet()
    val spaced = id.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    return spaced.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
}

/**
 * The input type of a web field. A node that Vanadium builds for a page field has input type 0 and says what it is only in its
 * HTML attributes (`type=password`) [measured: PWM-004, github.com/login and the-internet.herokuapp.com/login]; a node
 * with an input type of its own keeps it.
 */
fun webInputType(inputType: Int, htmlType: String?): Int {
    if (inputType != 0) return inputType
    return when (htmlType?.lowercase()) {
        "password" -> InputTypes.TEXT_WEB_PASSWORD
        "email" -> InputTypes.TEXT_WEB_EMAIL
        else -> inputType
    }
}

/** The id words of a web field: the id entry of the node, or when it has none the `id` and `name` of the HTML element. */
fun webIdEntry(idEntry: String?, htmlId: String?, htmlName: String?): String? {
    if (!idEntry.isNullOrEmpty()) return idEntry
    return listOfNotNull(htmlId, htmlName).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { null }
}

/**
 * What a field is, for the purpose of a login. The autofill hints of the app or page come first, because the author of
 * the form knows best; the input type and the words of the field id are the fallback.
 */
fun classify(hints: List<String>, inputType: Int, idEntry: String?): FieldRole {
    val normalised = hints.map { it.lowercase() }
    for (h in normalised) {
        when {
            h in USERNAME_HINTS -> return FieldRole.Username
            h in PASSWORD_HINTS -> return FieldRole.Password
            h.startsWith("credit") || h in setOf("search", "phone", "creditcardnumber", "smsotpcode", "postaladdress") -> return FieldRole.Other
        }
    }
    val words = tokens(idEntry)
    val klass = inputType and InputTypes.CLASS_MASK
    val variation = inputType and InputTypes.VARIATION_MASK
    val passwordType = (klass == InputTypes.CLASS_TEXT && variation in setOf(0x80, 0x90, 0xe0)) ||
        (klass == InputTypes.CLASS_NUMBER && variation == 0x10)
    if (passwordType) return FieldRole.Password
    if (words.any { it in NOT_LOGIN_TOKENS }) return FieldRole.Other
    val emailType = klass == InputTypes.CLASS_TEXT && variation in setOf(0x20, 0xd0)
    if (emailType) return FieldRole.Username
    return when {
        words.any { it in PASSWORD_TOKENS } -> FieldRole.Password
        words.any { it in USER_TOKENS } -> FieldRole.Username
        else -> FieldRole.Other
    }
}
