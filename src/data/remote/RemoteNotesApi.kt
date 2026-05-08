package com.jnet.notes.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class NoteDto(val id: Int, val title: String, val content: String, val timestamp: Long)
data class AuthResponse(val success: Boolean, val token: String, val message: String)

interface RemoteNotesApi {
    @FormUrlEncoded
    @POST("remote-notes.php")
    fun authenticate(
        @Field("action") action: String = "login",
        @Field("username") username: String,
        @Field("password") password: String
    ): retrofit2.Response<AuthResponse>

    @FormUrlEncoded
    @POST("remote-notes.php")
    fun uploadNote(
        @Field("action") action: String = "save",
        @Field("token") token: String,
        @Field("title") title: String,
        @Field("content") content: String
    ): retrofit2.Response<AuthResponse>

    @GET("remote-notes.php")
    fun fetchNotes(
        @Query("action") action: String = "list",
        @Query("token") token: String
    ): retrofit2.Response<List<NoteDto>>
}

fun createApi(): RemoteNotesApi {
    return Retrofit.Builder()
        .baseUrl("https://jnetai.com/apps/Notes/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RemoteNotesApi::class.java)
}
