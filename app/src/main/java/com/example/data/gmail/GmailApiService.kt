package com.example.data.gmail

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GmailApiService {

    @GET("gmail/v1/users/me/messages")
    suspend fun listMessages(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String? = null,
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null
    ): Response<GmailListMessagesResponse>

    @GET("gmail/v1/users/me/messages/{id}")
    suspend fun getMessage(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String,
        @Query("format") format: String = "full"
    ): Response<GmailMessageDetail>

    @POST("gmail/v1/users/me/messages/{id}/modify")
    suspend fun modifyMessage(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String,
        @Body body: GmailModifyRequest
    ): Response<GmailMessageDetail>

    @POST("gmail/v1/users/me/messages/{id}/trash")
    suspend fun trashMessage(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    ): Response<GmailMessageDetail>

    @DELETE("gmail/v1/users/me/messages/{id}")
    suspend fun deleteMessagePermanently(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    ): Response<Unit>

    @POST("gmail/v1/users/me/drafts")
    suspend fun createDraft(
        @Header("Authorization") authHeader: String,
        @Body body: GmailDraftRequest
    ): Response<GmailDraftResponse>

    @POST("gmail/v1/users/me/messages/send")
    suspend fun sendMessage(
        @Header("Authorization") authHeader: String,
        @Body body: GmailRawMessage
    ): Response<GmailMessageRef>
}
