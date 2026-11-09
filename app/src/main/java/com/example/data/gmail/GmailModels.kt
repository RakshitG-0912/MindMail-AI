package com.example.data.gmail

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GmailMessageRef(
    @Json(name = "id") val id: String,
    @Json(name = "threadId") val threadId: String? = null
)

@JsonClass(generateAdapter = true)
data class GmailListMessagesResponse(
    @Json(name = "messages") val messages: List<GmailMessageRef>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null,
    @Json(name = "resultSizeEstimate") val resultSizeEstimate: Int? = null
)

@JsonClass(generateAdapter = true)
data class GmailHeader(
    @Json(name = "name") val name: String,
    @Json(name = "value") val value: String
)

@JsonClass(generateAdapter = true)
data class GmailBody(
    @Json(name = "size") val size: Int? = null,
    @Json(name = "data") val data: String? = null
)

@JsonClass(generateAdapter = true)
data class GmailPart(
    @Json(name = "partId") val partId: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "filename") val filename: String? = null,
    @Json(name = "headers") val headers: List<GmailHeader>? = null,
    @Json(name = "body") val body: GmailBody? = null,
    @Json(name = "parts") val parts: List<GmailPart>? = null
)

@JsonClass(generateAdapter = true)
data class GmailPayload(
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "headers") val headers: List<GmailHeader>? = null,
    @Json(name = "body") val body: GmailBody? = null,
    @Json(name = "parts") val parts: List<GmailPart>? = null
)

@JsonClass(generateAdapter = true)
data class GmailMessageDetail(
    @Json(name = "id") val id: String,
    @Json(name = "threadId") val threadId: String? = null,
    @Json(name = "labelIds") val labelIds: List<String>? = null,
    @Json(name = "snippet") val snippet: String? = null,
    @Json(name = "internalDate") val internalDate: String? = null,
    @Json(name = "payload") val payload: GmailPayload? = null
)

@JsonClass(generateAdapter = true)
data class GmailModifyRequest(
    @Json(name = "addLabelIds") val addLabelIds: List<String> = emptyList(),
    @Json(name = "removeLabelIds") val removeLabelIds: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GmailRawMessage(
    @Json(name = "raw") val raw: String,
    @Json(name = "threadId") val threadId: String? = null
)

@JsonClass(generateAdapter = true)
data class GmailDraftRequest(
    @Json(name = "message") val message: GmailRawMessage
)

@JsonClass(generateAdapter = true)
data class GmailDraftResponse(
    @Json(name = "id") val id: String,
    @Json(name = "message") val message: GmailMessageRef? = null
)
