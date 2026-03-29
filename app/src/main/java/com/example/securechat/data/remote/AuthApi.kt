package com.example.securechat.data.remote

import com.example.securechat.domain.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: Map<String, String>): Response<User>

    @POST("auth/register")
    suspend fun register(@Body request: Map<String, String>): Response<User>
}
