package com.united.digitaldispatch.PSWDispatch

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModelFactory
import com.united.digitaldispatch.PSWDispatch.Adaptor.PSWDispatchHeaderAdapter
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.PswDispatchHeaderEntity
import com.united.digitaldispatch.utils.ConfirmDispatchDialog
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PswDispatch : AppCompatActivity() {

    companion object {
        const val EXTRA_SHIPMENT_NO = "extra_shipment_no"
        const val EXTRA_TRUCK_NO = "extra_truck_no"
    }

    private lateinit var sessionManager: SessionManager

    private lateinit var rvHeaders: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnCreateHeader: AppCompatButton
    private lateinit var btnProceed: AppCompatButton

    private val adapter = PSWDispatchHeaderAdapter()

    private var allHeaders: List<PswDispatchHeaderEntity> = emptyList()
    private var animateNextSubmit = true

    private val viewModel: PswDispatchViewModel by viewModels {
        PswDispatchViewModelFactory(
            MasterDataRepository(
                ApiClient.instance,
                AppDatabase.getInstance(applicationContext)
            )
        )
    }

    // -------------------------------------------------
    // ON CREATE
    // -------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_pswdispatch)

        sessionManager = SessionManager(this)

        initViews()
        setupList()
        setupSearch()
        setupClickListeners()
        observeHeaders()


    }

    override fun onResume() {
        super.onResume()

        // Coming back from Create Header
        animateNextSubmit = true

        viewModel.loadLocalPswHeaders()
    }

    // -------------------------------------------------
    // VIEWS
    // -------------------------------------------------

    private fun initViews() {

        rvHeaders = findViewById(R.id.rv_header_select)
        edtSearch = findViewById(R.id.edt_searchBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        btnBack = findViewById(R.id.btn_back)
        btnCreateHeader = findViewById(R.id.btn_createHeader)
        btnProceed = findViewById(R.id.btn_proceed)

        findViewById<TextView>(R.id.txt_org_code).text =
            sessionManager.getOrganizationCode().orEmpty()

        findViewById<TextView>(R.id.txt_date).text =
            SimpleDateFormat(
                "dd-MM-yyyy",
                Locale.US
            ).format(Date())
    }

    // -------------------------------------------------
    // LIST
    // -------------------------------------------------

    private fun setupList() {
        rvHeaders.adapter = adapter
    }

    // -------------------------------------------------
    // SEARCH
    // -------------------------------------------------

    private fun setupSearch() {

        edtSearch.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    applyFilter(
                        s?.toString().orEmpty()
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )
    }

    private fun applyFilter(query: String) {

        val q = query.trim()

        val filtered =
            if (q.isEmpty()) {

                allHeaders

            } else {

                allHeaders.filter {

                    it.shipmentNo.contains(
                        q,
                        ignoreCase = true
                    ) ||

                            it.senderTruckNo
                                .orEmpty()
                                .contains(
                                    q,
                                    ignoreCase = true
                                )
                }
            }

        val animate =
            animateNextSubmit &&
                    q.isEmpty() &&
                    filtered.isNotEmpty()

        adapter.submitList(filtered) {

            if (animate) {
                rvHeaders.scheduleLayoutAnimation()
            }
        }

        if (q.isEmpty()) {
            animateNextSubmit = false
        }

        if (filtered.isEmpty()) {

            tvEmpty.text =
                if (q.isEmpty()) {
                    "No PSW dispatch headers yet.\nTap Create Header to add one."
                } else {
                    "No header matches \"$q\""
                }

            tvEmpty.visibility = android.view.View.VISIBLE

        } else {

            tvEmpty.visibility = android.view.View.GONE
        }
    }

    // -------------------------------------------------
    // OBSERVE
    // -------------------------------------------------

    private fun observeHeaders() {

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.localPswHeaders.collect { list ->

                    allHeaders = list

                    applyFilter(
                        edtSearch.text.toString()
                    )
                }
            }
        }
    }

    // -------------------------------------------------
    // CLICKS
    // -------------------------------------------------

    private fun setupClickListeners() {

        btnBack.setOnClickListener {
            finish()
        }

        btnCreateHeader.setOnClickListener {

            startActivity(
                Intent(
                    this@PswDispatch,
                    CreateHeaderPSWDispatch::class.java
                )
            )
        }

        btnProceed.setOnClickListener {

            onProceedClicked()
        }
    }

    // -------------------------------------------------
    // PROCEED
    // -------------------------------------------------

    private fun onProceedClicked() {

        val selected = adapter.getSelected()

        if (selected == null) {

            Toast.makeText(
                this,
                "Select any header",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val unit =
            if (
                selected.uom.equals(
                    "KG",
                    ignoreCase = true
                )
            ) {
                "/Kg"
            } else {
                "/Truck"
            }

        val freight =
            selected.frieghtCharges?.let {

                if (it % 1.0 == 0.0) {
                    it.toLong().toString()
                } else {
                    it.toString()
                }

            } ?: "-"

        val rows = listOf(

            "Receiver" to
                    selected.receiverOrgnCode.orEmpty(),

            "Truck No" to
                    selected.senderTruckNo.orEmpty(),

            "RC No" to
                    selected.rcNo.orEmpty(),

            "Driver" to
                    selected.driverName.orEmpty(),

            "Truck Type" to
                    selected.typeOfTruck.orEmpty()
        )

        ConfirmDispatchDialog(

            activity = this,

            shipmentNo =
                selected.shipmentNo,

            rows = rows,

            freightText =
                "$freight $unit",

            title =
                "Proceed to PSW Details",

            confirmText =
                "Proceed"

        ) {

            openDetails(selected)

        }.show()
    }

    // -------------------------------------------------
    // OPEN PSW DETAILS
    // -------------------------------------------------

    private fun openDetails(
        header: PswDispatchHeaderEntity
    ) {

        val intent =
            Intent(
                this@PswDispatch,
                PSWDispatchDetails::class.java
            ).apply {

                putExtra(
                    EXTRA_SHIPMENT_NO,
                    header.shipmentNo
                )

                putExtra(
                    EXTRA_TRUCK_NO,
                    header.senderTruckNo.orEmpty()
                )
            }

        startActivity(intent)
    }
}