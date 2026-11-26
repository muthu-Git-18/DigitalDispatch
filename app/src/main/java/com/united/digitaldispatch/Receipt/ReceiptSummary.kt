package com.united.digitaldispatch.Receipt

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
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
import com.united.digitaldispatch.Receipt.Adaptor.ReceiptSummaryAdapter
import com.united.digitaldispatch.Receipt.Adaptor.ReceiptSummaryCalc
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import com.united.digitaldispatch.utils.CompleteConfirmDialog
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receipt Summary (grade-wise totals of one shipment) + Receipt Complete.
 * Step 1: UI + local data only. Complete calls are added once the API / SignalR parts are shared.
 */
class ReceiptSummary : AppCompatActivity() {

    private val viewModel: ReceiptViewModel by viewModels {
        ReceiptViewModelFactory(
            ReceiptRepository(ApiClient.instance, AppDatabase.getInstance(applicationContext))
        )
    }

    private lateinit var txtTitle: TextView
    private lateinit var txtOrgCode: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtHeaderId: TextView
    private lateinit var txtTotGrade: TextView
    private lateinit var txtTotBales: TextView
    private lateinit var txtTotQty: TextView
    private lateinit var txtNoData: TextView
    private lateinit var txtComplete: TextView
    private lateinit var rvItem: RecyclerView

    private val adapter = ReceiptSummaryAdapter()

    private var shipmentNo = ""
    private var orgType = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_summary)

        // same key the details screen uses to open this screen
        shipmentNo = intent.getStringExtra(Receipt.EXTRA_SHIPMENT_NO).orEmpty()
        orgType = SessionManager(this).getModuleType().orEmpty()
        Log.d("ReceiptSummary", "shipmentNo=[$shipmentNo]")

        bindViews()
        setContentData()
        rvItem.adapter = adapter
        observeData()
    }

    private fun bindViews() {
        txtTitle = findViewById(R.id.txt_title)
        txtOrgCode = findViewById(R.id.txt_org_code)
        txtDate = findViewById(R.id.txt_date)
        txtHeaderId = findViewById(R.id.txt_header_id)
        txtTotGrade = findViewById(R.id.txt_tot_grade)
        txtTotBales = findViewById(R.id.txt_tot_bales)
        txtTotQty = findViewById(R.id.txt_tot_qty)
        txtNoData = findViewById(R.id.txt_no_data)
        txtComplete = findViewById(R.id.txt_complete)
        rvItem = findViewById(R.id.rv_item)

        findViewById<FrameLayout>(R.id.constraint_back_container)
            .setOnClickListener { finish() }

        txtComplete.setOnClickListener { onCompleteClicked() }
    }

    private fun setContentData() {
        txtTitle.text = if (orgType.isNotEmpty()) "Receipt ($orgType)" else "Receipt"
        txtDate.text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        txtHeaderId.text = shipmentNo
    }

    private fun observeData() {
        viewModel.loadDetails(shipmentNo)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.details.collect { bales -> render(bales) }
            }
        }
    }

    private fun render(bales: List<ReceiptDetailEntity>) {
        val grades = ReceiptSummaryCalc.byGrade(bales)
        adapter.submitList(grades)

        val hasData = grades.isNotEmpty()
        rvItem.visibility = if (hasData) View.VISIBLE else View.GONE
        txtNoData.visibility = if (hasData) View.GONE else View.VISIBLE

        txtOrgCode.text = bales.firstOrNull()?.receiverOrgnCode.orEmpty()
        txtTotGrade.text = grades.size.toString()
        txtTotBales.text = bales.size.toString()
        txtTotQty.text = String.format(Locale.US, "%.2f", grades.sumOf { it.qty })
    }

    private fun onCompleteClicked() {
        val bales = viewModel.details.value
        val total = bales.size
        val received = bales.count { it.status == ReceiptDetailEntity.STATUS_RECEIVED }

        if (total == 0) {
            Toast.makeText(this, "No bales to complete", Toast.LENGTH_SHORT).show()
            return
        }
        // old rule: "Please Do Receipt for all Bales !!!"
        if (received < total) {
            Toast.makeText(this, "Please do receipt for all bales", Toast.LENGTH_LONG).show()
            return
        }

        CompleteConfirmDialog(
            activity = this,
            title = "Complete Receipt?",
            message = "Once completed, this receipt cannot be changed.",
            detail = "SHIPMENT  $shipmentNo",
            stats = listOf(
                "Bales" to total.toString(),
                "Qty" to txtTotQty.text.toString()
            )
        ) {
            // TODO (next step): header post + complete API + SignalR completion event
            // viewModel.completeReceipt(shipmentNo, ...)
        }.show()
    }
}