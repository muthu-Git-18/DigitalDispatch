package com.united.digitaldispatch.Apiservice.network

import com.united.digitaldispatch.Login.models.ItemMasterResponse
import com.united.digitaldispatch.Login.models.MasterDataResponse
import com.united.digitaldispatch.Login.models.StockResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("Master/master-data")
    suspend fun getMasterData(): Response<MasterDataResponse>

    @GET("Master/items")
    suspend fun getItems(
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSize") pageSize: Int
    ): Response<ItemMasterResponse>

    @GET("Master/stock")
    suspend fun getStock(
        @Query("orgnType") orgnType: String,
        @Query("orgnCode") orgnCode: String,
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSize") pageSize: Int
    ): Response<StockResponse>

}