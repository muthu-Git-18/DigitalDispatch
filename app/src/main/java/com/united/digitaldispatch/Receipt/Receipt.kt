package com.united.digitaldispatch.Receipt

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.ReceiptRepository
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptSyncState
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptViewModelFactory
import com.united.digitaldispatch.R
import com.united.digitaldispatch.Receipt.Adaptor.ReceiptHeaderAdapter
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.ReceiptHeaderEntity
import com.united.digitaldispatch.utils.AppLoader
import com.united.digitaldispatch.utils.ConfirmDispatchDialog
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Receipt : AppCompatActivity() {

    companion object {
        const val EXTRA_SHIPMENT_NO = "extra_receipt_shipment_no"
        const val EXTRA_TRUCK_NO = "extra_receipt_truck_no"
    }

    private lateinit var sessionManager: SessionManager

    private lateinit var rvHeaders: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnProceed: AppCompatButton

    private val adapter = ReceiptHeaderAdapter()
    private val loader by lazy { AppLoader(this) }

    private var allHeaders: List<ReceiptHeaderEntity> = emptyList()
    private var animateNextSubmit = true

    private val viewModel: ReceiptViewModel by viewModels {
        ReceiptViewModelFactory(
            ReceiptRepository(
                ApiClient.instance,
                AppDatabase.getInstance(applicationContext)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)

        sessionManager = SessionManager(this)

        initViews()
        rvHeaders.adapter = adapter
        setupSearch()
        setupClickListeners()
        observeHeaders()
        observeSync()

        loadFromServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        loader.dismiss()
    }

    private fun initViews() {
        rvHeaders = findViewById(R.id.rv_header_select)
        edtSearch = findViewById(R.id.edt_searchBar)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnBack = findViewById(R.id.btn_back)
        btnProceed = findViewById(R.id.btn_proceed)

        findViewById<TextView>(R.id.txt_org_code).text =
            sessionManager.getOrganizationCode().orEmpty()

        findViewById<TextView>(R.id.txt_date).text =
            SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
    }

    private fun loadFromServer() {
        val code = sessionManager.getOrganizationCode()
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Organization code missing", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.syncReceiptHeaders(code)
    }

    // ---------------- SEARCH (shipment / truck / RC no) ----------------

    private fun setupSearch() {
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFilter(query: String) {
        val q = query.trim()

        val filtered =
            if (q.isEmpty()) allHeaders
            else allHeaders.filter {
                it.shipmentNo.contains(q, ignoreCase = true) ||
                        it.senderTruckNo.orEmpty().contains(q, ignoreCase = true) ||
                        it.rcNo.orEmpty().contains(q, ignoreCase = true)
            }

        val animate = animateNextSubmit && q.isEmpty() && filtered.isNotEmpty()

        adapter.submitList(filtered) {
            if (animate) rvHeaders.scheduleLayoutAnimation()
        }

        if (q.isEmpty() && filtered.isNotEmpty()) animateNextSubmit = false

        if (filtered.isEmpty()) {
            tvEmpty.text =
                if (q.isEmpty()) "No receipts pending.\nPlease try again later."
                else "No receipt matches \"$q\""
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    // ---------------- OBSERVE ----------------

    private fun observeHeaders() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.headers.collect { list ->
                    allHeaders = list
                    applyFilter(edtSearch.text.toString())
                }
            }
        }
    }

    private fun observeSync() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.syncState.collect { state ->
                    when (state) {
                        is ReceiptSyncState.Idle -> {}

                        is ReceiptSyncState.Loading -> loader.show("Receipt Header Loading")

                        is ReceiptSyncState.Success -> {
                            loader.dismiss()
                            viewModel.resetSyncState()
                            animateNextSubmit = true
                            applyFilter(edtSearch.text.toString())
                        }

                        is ReceiptSyncState.Error -> {
                            loader.dismiss()
                            viewModel.resetSyncState()
                            Toast.makeText(
                                this@Receipt,
                                "${state.message}\nShowing saved receipts",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    // ---------------- CLICKS ----------------

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnProceed.setOnClickListener { onProceedClicked() }
    }

    private fun onProceedClicked() {
        val selected = adapter.getSelected()

        if (selected == null) {
            Toast.makeText(this, "Select any receipt", Toast.LENGTH_SHORT).show()
            return
        }

        val unit = if (selected.uom.equals("KG", ignoreCase = true)) "/Kg" else "/Truck"
        val freight = selected.frieghtCharges?.let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        } ?: "-"

        val rows = listOf(
            "Sender" to selected.senderOrgnCode.orEmpty(),
            "Truck No" to selected.senderTruckNo.orEmpty(),
            "RC No" to selected.rcNo.orEmpty(),
            "Driver" to selected.driverName.orEmpty(),
            "Sent By" to selected.sentBy.orEmpty()
        )

        ConfirmDispatchDialog(
            activity = this,
            shipmentNo = selected.shipmentNo,
            rows = rows,
            freightText = "$freight $unit",
            title = "Proceed to Receipt",
            confirmText = "Proceed"
        ) {
            startActivity(
                Intent(this@Receipt, ReceiptDetails::class.java).apply {
                    putExtra(EXTRA_SHIPMENT_NO, selected.shipmentNo)
                    putExtra(EXTRA_TRUCK_NO, selected.senderTruckNo.orEmpty())
                }
            )
        }.show()
    }
}