package net.matsudamper.folderviewer.repository

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.InputStream
import java.util.EnumSet

class SmbFileRepository : FileRepository {
    override suspend fun getFiles(path: String): List<FileItem> {
        // TODO: Implement SMB file listing
        return emptyList()
    }

    override suspend fun getFileContent(path: String): InputStream {
        // TODO: Implement SMB file content retrieval
        return InputStream.nullInputStream()
    }
}
