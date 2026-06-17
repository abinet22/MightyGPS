package com.example.data.api

import com.example.data.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TraccarApi {

    @POST("api/session")
    @FormUrlEncoded
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): User

    @GET("api/session")
    suspend fun getCurrentSession(): User

    @GET("api/devices")
    suspend fun getDevices(
        @Query("all") all: Boolean? = null,
        @Query("userId") userId: Long? = null
    ): List<Device>

    @POST("api/devices")
    suspend fun createDevice(@Body device: Device): Device

    @PUT("api/devices/{id}")
    suspend fun updateDevice(@Path("id") id: Long, @Body device: Device): Device

    @DELETE("api/devices/{id}")
    suspend fun deleteDevice(@Path("id") id: Long): retrofit2.Response<Unit>

    @GET("api/positions")
    suspend fun getLatestPositions(): List<Position>

    @GET("api/positions")
    suspend fun getPositions(
        @Query("deviceId") deviceId: Long,
        @Query("from") from: String,
        @Query("to") to: String
    ): List<Position>

    @GET("api/reports/route")
    suspend fun getRouteReport(
        @Query("deviceId") deviceId: Long,
        @Query("from") from: String,
        @Query("to") to: String
    ): List<Position>

    @GET("api/reports/events")
    suspend fun getEventsReport(
        @Query("deviceId") deviceId: Long,
        @Query("from") from: String,
        @Query("to") to: String
    ): List<Event>

    @GET("api/users")
    suspend fun getUsers(): List<User>

    @POST("api/users")
    suspend fun createUser(@Body user: User): User

    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Long, @Body user: User): User

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: Long): retrofit2.Response<Unit>

    @POST("api/commands/send")
    suspend fun sendCommand(@Body command: DeviceCommand): retrofit2.Response<Unit>

    @GET("api/geofences")
    suspend fun getGeofences(): List<TraccarGeofence>

    @POST("api/geofences")
    suspend fun createGeofence(@Body geofence: TraccarGeofence): TraccarGeofence

    @PUT("api/geofences/{id}")
    suspend fun updateGeofence(@Path("id") id: Long, @Body geofence: TraccarGeofence): TraccarGeofence

    @DELETE("api/geofences/{id}")
    suspend fun deleteGeofence(@Path("id") id: Long): retrofit2.Response<Unit>

    @POST("api/permissions")
    suspend fun linkGeofenceDevice(@Body permission: TraccarPermission): retrofit2.Response<Unit>


    companion object {
        fun create(baseUrl: String, credentialsProvider: () -> Pair<String, String>?): TraccarApi {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cookieJar(TraccarCookieJar)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val builder = original.newBuilder()
                    
                    // Inject basic authentication headers on all API calls automatically
                    val credentials = credentialsProvider()
                    if (credentials != null) {
                        val (email, password) = credentials
                        val basicToken = okhttp3.Credentials.basic(email, password)
                        builder.header("Authorization", basicToken)
                    }
                    
                    // Add standard accept headers
                    builder.header("Accept", "application/json")
                    
                    chain.proceed(builder.build())
                }
                .addInterceptor(loggingInterceptor)

            val moshi = com.squareup.moshi.Moshi.Builder()
                .add(DeviceAdapter())
                .add(PositionAdapter())
                .add(UserAdapter())
                .add(EventAdapter())
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(clientBuilder.build())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TraccarApi::class.java)
        }
    }
}
