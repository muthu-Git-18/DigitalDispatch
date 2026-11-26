package com.united.digitaldispatch.Receipt

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
import com.united.digitaldispatch.Apiservice.repository.ReceiptRepository
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptViewModelFactory
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import com.united.digitaldispatch.utils.SessionManager
import com.united.digitaldispatch.utils.showInfoDetails
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptView : AppCompatActivity() {

    private val viewModel: ReceiptViewModel by viewModels {
        ReceiptViewModelFactory(
            ReceiptRepository(ApiClient.instance, AppDatabase.getInstance(applicationContext))
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
    private lateinit var rvItem: RecyclerView

    private val adapter = ReceiptViewAdapter(onLongClick = { bale -> showBaleInfo(bale) })

    private var shipmentNo: String = ""
    private var orgType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_view)

        shipmentNo = intent.getStringExtra(EXTRA_SHIPMENT_NO).orEmpty()
        orgType = SessionManager(this).getModuleType().orEmpty()

        Log.d("ReceiptView", "shipmentNo=[$shipmentNo]")

        bindViews()
        setContentData()
        rvItem.adapter = adapter
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
        rvItem = findViewById(R.id.rv_item)

        findViewById<FrameLayout>(R.id.constraint_back_container)
            .setOnClickListener { finish() }
    }

    private fun setContentData() {
        txtTitle.text = if (orgType.isNotEmpty()) "Receipt ($orgType)" else "Receipt"
        txtDate.text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        txtHeaderId.text = shipmentNo
    }

    private fun observeBales() {
        viewModel.loadDetails(shipmentNo)   // starts the Room Flow for this shipment

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.details.collect { bales -> render(bales) }
            }
        }
    }

    private fun render(bales: List<ReceiptDetailEntity>) {
        Log.d("ReceiptView", "bales=${bales.size}")

        adapter.submitList(bales)
        txtNoData.visibility = if (bales.isNotEmpty()) View.GONE else View.VISIBLE

        txtOrgCode.text = bales.firstOrNull()?.receiverOrgnCode.orEmpty()

        txtTotBales.text = bales.size.toString()
        txtTotCount.text = bales
            .count { it.status == ReceiptDetailEntity.STATUS_RECEIVED }
            .toString()
        txtTotQuantity.text = String.format(Locale.US, "%.2f", bales.sumOf { it.markedWt })
    }

    private fun showBaleInfo(b: ReceiptDetailEntity) {
        val statusColor = when (b.syncStatus) {
            ReceiptDetailEntity.SYNC_SUCCESS -> Color.parseColor("#2E7D32")   // uploaded: green
            ReceiptDetailEntity.SYNC_FAILED -> Color.parseColor("#C0392B")    // failed: red
            else -> Color.parseColor("#D9822B")                               // pending: orange
        }

        showInfoDetails(
            title = "Bale Details",
            headerLabel = "HEADER ID",
            headerValue = shipmentNo,
            rows = listOf(
                "Bale No" to b.baleNumber,
                "Marked Wt" to b.markedWt.toString(),
                "Dispatch Wt" to b.dispatchWeight.toString(),
                "Receipt Wt" to (b.receiptWeight?.toString() ?: "-"),
                "Grade" to b.grade.orEmpty(),
                "Status" to b.status,
                "Device Id" to b.deviceId.orEmpty(),
                "Sync Status" to b.syncStatus,
                "Server Message" to b.serverMessage.orEmpty()
            ),
            highlight = mapOf("Sync Status" to statusColor)
        )
    }

    companion object {
        const val EXTRA_SHIPMENT_NO = "shipmentNo"
    }
}