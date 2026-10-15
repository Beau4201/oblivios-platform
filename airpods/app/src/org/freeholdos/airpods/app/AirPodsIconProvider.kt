// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import java.io.FileNotFoundException

/**
 * Serves the three ring icons Settings loads (a bitmap per URI string). Not exported; Settings gets
 * a persistable read grant from AirPodsMetadataWriter. A fixed allow-list of bundled PNGs: the path
 * of the URI is only a lookup key, never a file path.
 */
class AirPodsIconProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (mode != "r") throw SecurityException("read-only")
        val asset = ASSETS[uri.path] ?: throw FileNotFoundException()
        // Throws FileNotFoundException if the asset were stored compressed (PNG is not).
        return context!!.assets.openFd(asset)
    }

    override fun getType(uri: Uri): String? = if (ASSETS.containsKey(uri.path)) "image/png" else null

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    private companion object {
        val ASSETS = mapOf(
            "/v1/left" to "icons/left_v1.png",
            "/v1/right" to "icons/right_v1.png",
            "/v1/case" to "icons/case_v1.png",
        )
    }
}
