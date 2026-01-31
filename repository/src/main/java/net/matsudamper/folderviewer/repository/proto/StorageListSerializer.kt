package net.matsudamper.folderviewer.repository.proto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import com.google.protobuf.InvalidProtocolBufferException

internal object StorageListSerializer : Serializer<StorageListProto> {
    override val defaultValue: StorageListProto = StorageListProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): StorageListProto {
        try {
            return StorageListProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: StorageListProto, output: OutputStream) {
        t.writeTo(output)
    }
}
