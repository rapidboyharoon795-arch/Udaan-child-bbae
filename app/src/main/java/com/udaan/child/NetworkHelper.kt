package com.udaan.child

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {
    @Multipart @POST("/call-recording")
    suspend fun uploadRecording(@Part("deviceId") id: RequestBody, @Part("phoneNumber") num: RequestBody, @Part("callType") type: RequestBody, @Part("duration") dur: RequestBody, @Part("timestamp") ts: RequestBody, @Part rec: MultipartBody.Part): Response<Unit>

    @POST("/call-log") @Body suspend fun uploadCallLog(log: CallLogPayload): Response<Unit>
    @POST("/heartbeat") @Body suspend fun sendHeartbeat(hb: HeartbeatPayload): Response<Unit>
}

data class CallLogPayload(val deviceId: String, val phoneNumber: String, val callType: String, val duration: Long, val timestamp: Long, val contactName: String)
data class HeartbeatPayload(val deviceId: String, val timestamp: Long, val battery: Int)

object NetworkHelper {
    val apiService: ApiService by lazy {
        Retrofit.Builder().baseUrl("http://139.59.66.214:3000/").addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}
