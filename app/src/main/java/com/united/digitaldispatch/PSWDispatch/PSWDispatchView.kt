package com.united.digitaldispatch.PSWDispatch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.Dispatch.Adaptor.DispatchViewHeaderAdapter
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.PswHeaderState
import com.united.digitaldispatch.PSWDispatch.Adaptor.PSWDispatchViewAdapter
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.PSWDispatchDetailEntity
import com.united.digitaldispatch.utils.showInfoDetails
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PSWDispatchView : AppCompatActivity() {

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
    private lateinit var txtTotCount: TextView
    private lateinit var txtTotBales: TextView
    private lateinit var txtTotQuantity: TextView
    private lateinit var txtNoData: TextView

    private lateinit var rvHeader: RecyclerView
    private lateinit var rvItem: RecyclerView

    private var shipmentNo = ""

    private val adapter = PSWDispatchViewAdapter(
        onClick = { bale -> openBale(bale) },
        onLongClick = { bale -> showBaleInfo(bale) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswdispatch_view)

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

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun setContentData() {
        txtTitle.text = "PSW Dispatch (View)"

        txtDate.text = SimpleDateFormat(
            "dd-MM-yyyy",
            Locale.getDefault()
        ).format(Date())

        txtHeaderId.text = shipmentNo
    }

    private fun setupLists() {
        rvHeader.adapter = DispatchViewHeaderAdapter(false)
        rvItem.adapter = adapter
    }

    private fun observeBales() {
        viewModel.loadPswDetails(shipmentNo)
        viewModel.loadPswHeader(shipmentNo)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.details.collect { bales ->
                        render(bales)
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

    private fun render(
        bales: List<PSWDispatchDetailEntity>
    ) {
        adapter.submitList(bales)

        txtNoData.visibility =
            if (bales.isEmpty()) View.VISIBLE else View.GONE

        txtTotBales.text = bales.size.toString()

        txtTotCount.text = bales
            .mapNotNull { it.grade?.takeIf(String::isNotBlank) }
            .distinct()
            .size
            .toString()

        val totalMarkedWeight = bales.sumOf { it.markedWt }

        txtTotQuantity.text = String.format(
            Locale.US,
            "%.1f",
            totalMarkedWeight
        )
    }

    private fun openBale(
        bale: PSWDispatchDetailEntity
    ) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_BALE_NO, bale.baleNumber)
        )
        finish()
    }
    private fun showBaleInfo(
        bale: PSWDispatchDetailEntity
    ) {
        val statusColor = when (bale.syncStatus) {
            PSWDispatchDetailEntity.SYNC_SUCCESS ->
                Color.parseColor("#2E7D32")

            PSWDispatchDetailEntity.SYNC_FAILED ->
                Color.parseColor("#C0392B")

            else ->
                Color.parseColor("#D9822B")
        }

        showInfoDetails(
            title = "Bale Details",
            headerLabel = "HEADER ID",
            headerValue = shipmentNo,
            rows = listOf(
                "Bale No" to bale.baleNumber,
                "Marked Wt" to bale.markedWt.toString(),
                "Dispatch Wt" to bale.dispatchWeight.toString(),
                "Grade" to bale.grade.orEmpty(),
                "Price" to bale.price.orEmpty(),
                "Device Id" to bale.deviceId.orEmpty(),
                "Sync Status" to bale.syncStatus,
                "Server Message" to bale.serverMessage.orEmpty()
            ),
            highlight = mapOf(
                "Sync Status" to statusColor
            )
        )

    }

    companion object {
        const val EXTRA_BALE_NO = "pswBaleNo"
    }
}
