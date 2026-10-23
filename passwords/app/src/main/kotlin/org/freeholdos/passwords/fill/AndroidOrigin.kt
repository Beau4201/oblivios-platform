// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import java.security.MessageDigest

/**
 * The package manager's answers for [OriginPolicy]. The browsers are found with the `<queries>` entry of the manifest (a
 * package is only visible to the app when its intent filters match one); a package that is not visible is not a browser,
 * not a system app and has no digest, so it is never trusted.
 */
class AndroidPackageFacts(private val context: Context) : PackageFacts {
    private val pm: PackageManager get() = context.packageManager

    override fun isSystemApp(pkg: String): Boolean = try {
        pm.getApplicationInfo(pkg, 0).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun isBrowser(pkg: String): Boolean = browsers().contains(pkg)

    override fun signerDigest(pkg: String): String? = try {
        val info = pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        val signers = info.signingInfo?.apkContentsSigners.orEmpty()
        if (signers.isEmpty()) null else signers.map { sha256(it.toByteArray()) }.sorted().joinToString(":")
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** Every package that opens `https` links from another app: what the settings list offers and what the policy calls a browser. */
    fun browsers(): Set<String> {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        return pm.queryIntentActivities(view, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            .map { it.activityInfo.packageName }.toSet()
    }

    fun label(pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** The browsers the user trusts, in the private preferences of the app (package name to signer digest). */
class PrefsTrustStore(private val prefs: SharedPreferences) : TrustStore {
    override fun digestOf(pkg: String): String? = prefs.getString(pkg, null)
    override fun put(pkg: String, digest: String) = prefs.edit().putString(pkg, digest).apply()
    override fun remove(pkg: String) = prefs.edit().remove(pkg).apply()
}
