package com.united.digitaldispatch.Apiservice.network

import com.united.digitaldispatch.Dispatch.models.CompleteDispatchRequest
import com.united.digitaldispatch.Dispatch.models.CompleteDispatchResponse
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
import com.united.digitaldispatch.Receipt.models.ReceiptBaleListResponse
import com.united.digitaldispatch.Receipt.models.ReceiptHeaderListResponse
import com.united.digitaldispatch.Receipt.models.ReceiptUploadRequest
import com.united.digitaldispatch.Receipt.models.ReceiptUploadResponse
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


    ////////////////////////////////Masters Over ----Dispatch Starts Here-----/////////////////////////

    @POST("Dispatch/create-dispatch-header")
    suspend fun createDispatchHeader(
        @Body request: CreateDispatchHeaderRequest
    ): Response<CreateDispatchHeaderResponse>

    @GET("Dispatch/GetDispatch-headers")
    suspend fun getDispatchHeaders(
        @Query("orgnCode") orgnCode: String
    ): Response<DispatchHeaderListResponse>

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

    // ApiService
    @POST("Dispatch/complete-dispatch")
    suspend fun completeDispatch(
        @Body request: CompleteDispatchRequest
    ): Response<CompleteDispatchResponse>




   ////////////////////////////////Dispatch Over ----PSW Dispatch Starts Here-----/////////////////////////




    // PSW DISPATCH - vikram
    @POST("PSW_Dispatch/create-PSW-dispatch-header")
    suspend fun createPswDispatchHeader(
        @Body request: CreatePSWDispatchHeaderRequest
    ): Response<CreatePSWDispatchHeaderResponse>

    @GET("PSW_Dispatch/PSW-dispatch-headers")
    suspend fun getPswDispatchHeaders(
        @Query("orgnCode") orgnCode: String
    ): Response<PSWDispatchHeaderListResponse>

    // PSW upload (same request / response as Dispatch)
    @POST("PSW_Dispatch/Insert_PSW_DispatchBales")
    suspend fun uploadPswDispatchBales(
        @Body request: DispatchUploadRequest
    ): Response<DispatchUploadResponse>

    // PSW return sync
    @GET("PSW_Dispatch/sync-PSW_dispatch-changes?")
    suspend fun getPswDispatchChanges(
        @Query("shipmentNo") shipmentNo: String,
        @Query("fromChangeId") fromChangeId: Long
    ): Response<ResponseBody>

    ////////////////////////////////PSW Dispatch Over ----Receipt Starts Here-----/////////////////////////

    @GET("Receipt/receipt-headers")
    suspend fun getReceiptHeaders(
        @Query("recOrgnCode") recOrgnCode: String
    ): Response<ReceiptHeaderListResponse>


    @GET("Receipt/receipt-bales")
    suspend fun getReceiptBales(
        @Query("shipmentNo") shipmentNo: String      // Retrofit encodes 2025/P27/... to %2F for you
    ): Response<ReceiptBaleListResponse>

    @POST("Receipt/Insert_ReceiptBales")
    suspend fun uploadReceiptBales(@Body request: ReceiptUploadRequest): Response<ReceiptUploadResponse>

    @GET("Receipt/sync-receipt-changes")
    suspend fun getReceiptChanges(
        @Query("shipmentNo") shipmentNo: String,
        @Query("fromChangeId") fromChangeId: Long
    ): Response<ResponseBody>


}