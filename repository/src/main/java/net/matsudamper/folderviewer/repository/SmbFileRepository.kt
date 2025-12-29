package net.matsudamper.folderviewer.repository

import java.io.InputStream

class SmbFileRepository : FileRepository {
    override suspend fun getFiles(path: String): List<FileItem> {
        // TODO: SMBファイル一覧の実装
        return emptyList()
    }

    override suspend fun getFileContent(path: String): InputStream {
        // TODO: SMBファイルコンテンツ取得の実装
        TODO()
    }
}
