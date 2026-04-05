package net.matsudamper.folderviewer.dao.graphapi

import android.util.Log
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json

class GraphApiClient(
    private val tenantId: String,
    private val clientId: String,
    private val clientSecret: String,
    private val objectId: String,
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
    }

    private var cachedToken: String? = null

    private suspend fun getAccessToken(): String {
        cachedToken?.let { return it }

        val response = client.submitForm(
            url = "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token",
            formParameters = parameters {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("scope", "https://graph.microsoft.com/.default")
                append("grant_type", "client_credentials")
            },
        ) {
            header("Content-Type", "application/x-www-form-urlencoded")
        }.body<TokenResponse>()

        return response.accessToken.also { cachedToken = it }
    }

    suspend fun getDrive(objectId: String): DriveResponse {
        val token = getAccessToken()
        return client.get("https://graph.microsoft.com/v1.0/users/$objectId/drive") {
            header("Authorization", "Bearer $token")
        }.body()
    }

    /**
     * https://learn.microsoft.com/ja-jp/graph/api/driveitem-list-children
     */
    suspend fun getDriveItemChildren(
        itemId: String?,
    ): DriveItemCollectionResponse {
        val token = getAccessToken()
        val selectParam = listOf(
            "id",
            "name",
            "folder",
            "size",
            "lastModifiedDateTime",
        ).joinToString(",")
        return client.get(
            if (itemId == null) {
                "https://graph.microsoft.com/v1.0/users/$objectId/drive/root/children?select=$selectParam"
            } else {
                "https://graph.microsoft.com/v1.0/users/$objectId/drive/items/$itemId/children?select=$selectParam"
            }
                .also { Log.d("LOG", "$it") },
        ) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getDriveItemWithDownloadUrl(itemId: String): DriveItemWithDownloadUrlResponse {
        val token = getAccessToken()
        val selectParam = "id,name,@microsoft.graph.downloadUrl"
        return client.get(
            "https://graph.microsoft.com/v1.0/users/$objectId/drive/items/$itemId?select=$selectParam",
        ) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getDriveItem(itemId: String): DriveItemResponse {
        val token = getAccessToken()
        val selectParam = "id,name,size,lastModifiedDateTime"
        return client.get(
            "https://graph.microsoft.com/v1.0/users/$objectId/drive/items/$itemId?select=$selectParam",
        ) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
        }.body()
    }

    fun close() {
        client.close()
    }
}
