package net.matsudamper.folderviewer.repository

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SharePoint用のFileRepository実装
 */
class SharePointFileRepository(
    private val config: StorageConfiguration.SharePoint,
) : FileRepository {
    override suspend fun getFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        // TODO: Microsoft Graph APIを使用してSharePointからファイルリストを取得
        // 例: GET https://graph.microsoft.com/v1.0/sites/{site-id}/drive/root:/path:/children
        emptyList()
    }

    override suspend fun getFileContent(path: String): InputStream = withContext(Dispatchers.IO) {
        // TODO: Microsoft Graph APIを使用してファイルコンテンツを取得
        // 例: GET https://graph.microsoft.com/v1.0/sites/{site-id}/drive/items/{item-id}/content
        ByteArrayInputStream(ByteArray(0))
    }

    override suspend fun getThumbnail(path: String, thumbnailSize: Int): InputStream = withContext(Dispatchers.IO) {
        // TODO: Microsoft Graph APIを使用してサムネイルを取得
        // 例: GET https://graph.microsoft.com/v1.0/sites/{site-id}/drive/items/{item-id}/thumbnails
        getFileContent(path)
    }
}
