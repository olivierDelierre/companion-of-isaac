package app.isaaccompanion.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.isaaccompanion.resources.ResourceSource
import app.isaaccompanion.resources.ResourceTree

/** Resources read through the Storage Access Framework from a folder the user picked once. */
fun safResourceSource(context: Context, treeUri: Uri): ResourceSource {
    val resolver = context.contentResolver
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    return ResourceTree(
        rootId = rootId,
        list = { docId ->
            val out = HashMap<String, Pair<String, Boolean>>()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            runCatching {
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                        out[c.getString(1).lowercase()] = c.getString(0) to isDir
                    }
                }
            }
            out
        },
        openFile = { docId ->
            runCatching { resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)) }.getOrNull()
        },
    )
}
