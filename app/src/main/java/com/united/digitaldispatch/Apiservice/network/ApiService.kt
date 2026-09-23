package com.united.digitaldispatch.Apiservice.network

import com.united.digitaldispatch.Login.models.MasterDataResponse
import retrofit2.Response
import retrofit2.http.GET

interface ApiService {

    @GET("Master/master-data")
    suspend fun getMasterData(): Response<MasterDataResponse>

}