
package com.united.digitaldispatch.PSWDispatch

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.PswHeaderState
import com.united.digitaldispatch.Dispatch.Adaptor.DispatchSummaryHeaderAdapter
import com.united.digitaldispatch.PSWDispatch.Adaptor.PSWDispatchSummaryAdapter
import com.united.digitaldispatch.PSWDispatch.Adaptor.PSWGradeSummary
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.PSWDispatchDetailEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PSWDispatchSummary : AppCompatActivity() {

    private val viewModel: PswDispatchViewModel by viewModels {
        PswDispatchViewModelFactory(
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
    private lateinit var txtNoData: TextView

    private lateinit var rvHeader: RecyclerView
    private lateinit var rvItem: RecyclerView

    private lateinit var adapter: PSWDispatchSummaryAdapter

    private var shipmentNo = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswdispatch_summary)

        shipmentNo = intent
            .getStringExtra(PswDispatch.EXTRA_SHIPMENT_NO)
            .orEmpty()

        if (shipmentNo.isBlank()) {
            finish()
            return
        }

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

        txtNoData = findViewById(R.id.txt_no_data)

        rvHeader = findViewById(R.id.rv_header)
        rvItem = findViewById(R.id.rv_item)

        findViewById<FrameLayout>(R.id.constraint_back_container)
            .setOnClickListener {
                finish()
            }
    }

    private fun setContentData() {
        txtTitle.text = "PSW Dispatch"
        txtDate.text = SimpleDateFormat(
            "dd-MM-yyyy",
            Locale.getDefault()
        ).format(Date())

        txtHeaderId.text = shipmentNo

        // PSW summary has only Grade, Bales and Qty.
        findViewById<View>(R.id.statsRow2).visibility = View.GONE
    }

    private fun setupLists() {
        adapter = PSWDispatchSummaryAdapter()

        // Reuse the normal Dispatch header.
        // false hides the Rate and Value columns.
        rvHeader.adapter = DispatchSummaryHeaderAdapter(showValue = false)

        rvItem.adapter = adapter
    }

    private fun observeData() {
        viewModel.loadPswDetails(shipmentNo)
        viewModel.loadPswHeader(shipmentNo)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.details.collect { details ->
                        render(details)
                    }
                }

                launch {
                    viewModel.header.collect { state ->
                        if (state is PswHeaderState.Loaded) {
                            txtOrgCode.text =
                                state.header.senderOrgnCode.orEmpty()
                        }
                    }
                }
            }
        }
    }

    private fun render(details: List<PSWDispatchDetailEntity>) {

        val gradeSummary = buildGradeSummary(details)

        adapter.submitList(gradeSummary)

        val hasData = gradeSummary.isNotEmpty()

        rvItem.visibility = if (hasData) View.VISIBLE else View.GONE
        txtNoData.visibility = if (hasData) View.GONE else View.VISIBLE

        val totalQty = gradeSummary.sumOf { it.qty }

        txtTotGrade.text = gradeSummary.size.toString()
        txtTotBales.text = details.size.toString()
        txtTotQty.text = String.format(Locale.US, "%.1f", totalQty)
    }

    private fun buildGradeSummary(
        details: List<PSWDispatchDetailEntity>
    ): List<PSWGradeSummary> {

        return details
            .mapNotNull { detail ->
                detail.grade
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { grade -> grade to detail }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            .map { (grade, items) ->
                PSWGradeSummary(
                    grade = grade,
                    bales = items.size,
                    qty = items.sumOf { it.markedWt }
                )
            }
            .sortedBy { it.grade }
    }
}
