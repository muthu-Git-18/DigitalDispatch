package com.united.digitaldispatch.Dispatch

import android.os.Bundle
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
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.HeaderState
import com.united.digitaldispatch.Dispatch.Adaptor.DispatchSummaryAdapter
import com.united.digitaldispatch.Dispatch.Adaptor.DispatchSummaryHeaderAdapter
import com.united.digitaldispatch.Dispatch.models.BaleDetail
import com.united.digitaldispatch.Dispatch.models.DispatchSummaryCalc
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dispatch Summary (grade-wise totals of one shipment).
 *
 *  TAP            -> Grade / Bales / Qty / Rate / Value  + Total Value, Average Rate, Weight Loss
 *  PPD / GLT / WH -> Grade / Bales / Qty only (second stats row hidden)
 */
class DispatchSummary : AppCompatActivity() {

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
    private lateinit var txtTotGrade: TextView
    private lateinit var txtTotBales: TextView
    private lateinit var txtTotQty: TextView
    private lateinit var txtTotVal: TextView
    private lateinit var txtAvgRate: TextView
    private lateinit var txtWeightLoss: TextView
    private lateinit var txtNoData: TextView
    private lateinit var txtComplete: TextView
    private lateinit var rvHeader: RecyclerView
    private lateinit var rvItem: RecyclerView

    private var shipmentNo = ""
    private var orgType = ""
    private var isTap = false

    private lateinit var adapter: DispatchSummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispatch_summary)

        // same key the Details screen uses to open this screen
        shipmentNo = intent.getStringExtra(Dispatch.EXTRA_SHIPMENT_NO).orEmpty()
        orgType = SessionManager(this).getModuleType().orEmpty()
        isTap = orgType == "TAP"

        bindViews()
        setContentData()
        setupLists()
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
        txtTotVal = findViewById(R.id.txt_tot_val)
        txtAvgRate = findViewById(R.id.txt_avg_rate)
        txtWeightLoss = findViewById(R.id.txt_weight1)
        txtNoData = findViewById(R.id.txt_no_data)
        txtComplete = findViewById(R.id.txt_complete)
        rvHeader = findViewById(R.id.rv_header)
        rvItem = findViewById(R.id.rv_item)

        findViewById<FrameLayout>(R.id.constraint_back_container)
            .setOnClickListener { finish() }

        // Complete is done in the next step
        txtComplete.setOnClickListener {
            Toast.makeText(this, "Complete will be added next", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setContentData() {
        txtTitle.text = if (orgType.isNotEmpty()) "Dispatch ($orgType)" else "Dispatch"
        txtDate.text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        txtHeaderId.text = shipmentNo

        // Total Value / Average Rate / Weight Loss only for TAP (old: lnAvRate GONE)
        findViewById<View>(R.id.statsRow2).visibility = if (isTap) View.VISIBLE else View.GONE
    }

    private fun setupLists() {
        adapter = DispatchSummaryAdapter(showValue = isTap)
        rvHeader.adapter = DispatchSummaryHeaderAdapter(showValue = isTap)
        rvItem.adapter = adapter
    }

    private fun observeData() {
        viewModel.loadDetails(shipmentNo)
        viewModel.loadHeader(shipmentNo)

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

        val grades = DispatchSummaryCalc.byGrade(bales)

        adapter.submitList(grades)

        val hasData = grades.isNotEmpty()
        rvItem.visibility = if (hasData) View.VISIBLE else View.GONE
        txtNoData.visibility = if (hasData) View.GONE else View.VISIBLE

        val totalQty = grades.sumOf { it.qty }

        txtTotGrade.text = grades.size.toString()
        txtTotBales.text = bales.size.toString()
        txtTotQty.text = String.format(Locale.US, "%.1f", totalQty)

        if (isTap) {
            val totalValue = grades.sumOf { it.value }
            val weightLoss = grades.sumOf { it.qty - it.dispatchQty }
            val avgRate = if (totalQty > 0) totalValue / totalQty else 0.0

            txtTotVal.text = String.format(Locale.US, "%.2f", totalValue)
            txtAvgRate.text = String.format(Locale.US, "%.2f", avgRate)
            txtWeightLoss.text = String.format(Locale.US, "%.2f", weightLoss)
        }
    }
}