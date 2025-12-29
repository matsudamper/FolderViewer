package net.matsudamper.folderviewer.repository

import java.io.InputStream

class SmbFileRepository : FileRepository {
    override suspend fun getFiles(path: String): List<FileItem> {
        // TODO: Implement SMB file listing
        return emptyList()
    }

    override suspend fun getFileContent(path: String): InputStream {
        // TODO: Implement SMB file content retrieval
        TODO()
    }
}
