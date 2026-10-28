package com.united.digitaldispatch.Apiservice.network

import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderRequest
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderResponse
import com.united.digitaldispatch.Dispatch.models.DispatchHeaderListResponse
import com.united.digitaldispatch.Dispatch.models.DispatchUploadRequest
import com.united.digitaldispatch.Dispatch.models.DispatchUploadResponse
import com.united.digitaldispatch.Login.models.ItemMasterResponse
import com.united.digitaldispatch.Login.models.MasterDataResponse
import com.united.digitaldispatch.Login.models.StockResponse
import com.united.digitaldispatch.Login.models.TruckMasterResponse
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderRequest
import com.united.digitaldispatch.PSWDispatch.models.PSWDispatchHeaderListResponse
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderResponse
import okhttp3.ResponseBody


import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

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


    @GET("Master/truck-master")
    suspend fun getTruckMaster(
        @Query("fromOrgn") fromOrgn: String
    ): Response<TruckMasterResponse>

    @POST("Dispatch/create-dispatch-header")
    suspend fun createDispatchHeader(
        @Body request: CreateDispatchHeaderRequest
    ): Response<CreateDispatchHeaderResponse>

    @GET("Dispatch/GetDispatch-headers")
    suspend fun getDispatchHeaders(
        @Query("orgnCode") orgnCode: String
    ): Response<DispatchHeaderListResponse>

    // PSW DISPATCH - vikram
    @POST("PSW_Dispatch/create-PSW-dispatch-header")
    suspend fun createPswDispatchHeader(
        @Body request: CreatePSWDispatchHeaderRequest
    ): Response<CreatePSWDispatchHeaderResponse>

    @GET("PSW_Dispatch/PSW-dispatch-headers")
    suspend fun getPswDispatchHeaders(
        @Query("orgnCode") orgnCode: String
    ): Response<PSWDispatchHeaderListResponse>

  /*  // TODO: replace with the real delete-shipment endpoint / method / body
    @POST("Dispatch/delete-dispatch-header")
    suspend fun deleteDispatchHeader(
        @Body request: DeleteDispatchHeaderRequest
    ): Response<ApiMessageResponse>*/

    @POST("Dispatch/Insert_DispatchBales")
    suspend fun uploadDispatchBales(
        @Body request: DispatchUploadRequest
    ): Response<DispatchUploadResponse>

    /** return sync - full url is passed in (see DISPATCH_CHANGES_URL in the repository) */
    @GET("Dispatch/sync-dispatch-changes")
    suspend fun getDispatchChanges(
        @Query("shipmentNo") shipmentNo: String,
        @Query("fromChangeId") fromChangeId: Long
    ): Response<ResponseBody>
}