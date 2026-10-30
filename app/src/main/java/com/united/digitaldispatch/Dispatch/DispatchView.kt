package com.united.digitaldispatch.Dispatch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.HeaderState
import com.united.digitaldispatch.Dispatch.Adaptor.DispatchViewAdapter
import com.united.digitaldispatch.Dispatch.Adaptor.DispatchViewHeaderAdapter
import com.united.digitaldispatch.Dispatch.models.BaleDetail
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.utils.SessionManager
import com.united.digitaldispatch.utils.showInfoDetails
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DispatchView : AppCompatActivity() {

    // same ViewModel as the other dispatch screens
    private val viewModel: DispatchViewModel by viewModels {
        DispatchViewModelFactory(
            MasterDataRepository(
                ApiClient.instance,
                AppDatabase.getInstance(applicationContext)
            )
        )
    }

    private lateinit var txtTitle: TextView
    private lateinit var txtOrgCode: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtHeaderId: TextView
    private lateinit var txtTotCount: TextView
    private lateinit var txtTotBales: TextView
    private lateinit var txtTotQuantity: TextView
    private lateinit var txtNoData: TextView
    private lateinit var rvHeader: RecyclerView
    private lateinit var rvItem: RecyclerView

    private val adapter = DispatchViewAdapter(
        onClick = { bale -> openBale(bale) },
        onLongClick = { bale -> showBaleInfo(bale) }
    )
    private var shipmentNo: String = ""
    private var orgType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispatch_view)

        shipmentNo = intent.getStringExtra(EXTRA_SHIPMENT_NO).orEmpty()
        // after
        orgType = SessionManager(this).getModuleType().orEmpty()

        Log.d("DispatchView", "shipmentNo=[$shipmentNo]")

        bindViews()
        setContentData()
        setupLists()
        observeBales()
    }

    private fun bindViews() {
        txtTitle = findViewById(R.id.txt_title)
        txtOrgCode = findViewById(R.id.txt_org_code)
        txtDate = findViewById(R.id.txt_date)
        txtHeaderId = findViewById(R.id.txt_header_id)
        txtTotCount = findViewById(R.id.txt_tot_count)
        txtTotBales = findViewById(R.id.txt_tot_bales)
        txtTotQuantity = findViewById(R.id.txt_total_quantity)
        txtNoData = findViewById(R.id.txt_no_data)
        rvHeader = findViewById(R.id.rv_header)
        rvItem = findViewById(R.id.rv_item)

        findViewById<FrameLayout>(R.id.constraint_back_container)
            .setOnClickListener { finish() }
    }

    private fun setContentData() {
        txtTitle.text = if (orgType.isNotEmpty()) "Dispatch ($orgType)" else "Dispatch"
        txtDate.text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        txtHeaderId.text = shipmentNo
    }

    private fun setupLists() {
        val showDate = orgType == "TAP"
        adapter.showPurchaseDate = showDate
        rvHeader.adapter = DispatchViewHeaderAdapter(showDate)
        rvItem.adapter = adapter
    }

    private fun observeBales() {
        viewModel.loadDetails(shipmentNo)   // starts the Room Flow for this shipment
        viewModel.loadHeader(shipmentNo)    // for the org code

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.details.collect { bales -> render(bales) }
                }
                launch {
                    viewModel.header.collect { state ->
                        if (state is HeaderState.Loaded) {
                            txtOrgCode.text = state.header.senderOrgnCode.orEmpty()
                        }
                    }
                }
            }
        }
    }

    private fun render(bales: List<BaleDetail>) {
        adapter.submitList(bales)

        txtNoData.visibility = if (bales.isNotEmpty()) View.GONE else View.VISIBLE

        txtTotBales.text = bales.size.toString()
        txtTotCount.text = bales.map { it.grade }
            .filter { it.isNotBlank() }
            .distinct()
            .size
            .toString()

        val qty = bales.sumOf { it.markedWeight.toDoubleOrNull() ?: 0.0 }
        txtTotQuantity.text = String.format(Locale.US, "%.1f", qty)
    }

    private fun openBale(bale: BaleDetail) {
        // tapping a row returns that bale number to the caller
        setResult(RESULT_OK, Intent().putExtra(EXTRA_BALE_NO, bale.gpilBaleNumber))
        finish()
    }

    private fun showBaleInfo(b: BaleDetail) {

        val statusColor = when (b.syncStatus) {
            BaleDetail.SYNC_SUCCESS -> Color.parseColor("#2E7D32")   // uploaded: green
            BaleDetail.SYNC_FAILED -> Color.parseColor("#C0392B")    // failed: red
            else -> Color.parseColor("#D9822B")                      // pending: orange
        }

        showInfoDetails(
            title = "Bale Details",
            headerLabel = "HEADER ID",
            headerValue = shipmentNo,
            rows = listOf(
                "Bale No" to b.gpilBaleNumber,
                "Marked Wt" to b.markedWeight,
                "Dispatch Wt" to b.dispatchWeight,
                "Grade" to b.grade,
                "Price" to b.price,
                "Device Id" to b.deviceId,
                "Sync Status" to b.syncStatus,
                "Server Message" to b.serverMessage
            ),
            highlight = mapOf("Sync Status" to statusColor)
        )
    }

    companion object {
        const val EXTRA_SHIPMENT_NO = "shipmentNo"
        const val EXTRA_ORG_TYPE = "orgType"
        const val EXTRA_BALE_NO = "gpiBaleNo"
    }
}