package com.united.digitaldispatch.Dispatch

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.TruckMasterState
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.TruckMasterEntity
import com.united.digitaldispatch.utils.SearchableSpinnerDialog
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class CreateHeaderDispatch : AppCompatActivity() {

    // -------------------------------------------------
    // SESSION / DATABASE
    // -------------------------------------------------

    private lateinit var sessionManager: SessionManager
    private lateinit var db: AppDatabase

    private val ioExecutor = Executors.newSingleThreadExecutor()

    // -------------------------------------------------
    // VIEWS
    // -------------------------------------------------

    private lateinit var btnBack: ImageView

    private lateinit var txtSenderOrg: TextView
    private lateinit var tvReceiverOrg: TextView
    private lateinit var tvTruckType: TextView

    private lateinit var spTransporterName: Spinner

    private lateinit var rb20Weighment: RadioButton
    private lateinit var rb100Weighment: RadioButton

    private lateinit var edtTruckNo: EditText
    private lateinit var edtRcNo: EditText
    private lateinit var edtDriverName: EditText
    private lateinit var edtDriverLicense: EditText
    private lateinit var edtFreightCharges: EditText

    // -------------------------------------------------
    // ORGANIZATIONS
    // -------------------------------------------------

    private var allOrganizations: List<OrganizationEntity> = emptyList()

    private var selectedSenderOrganization: OrganizationEntity? = null
    private var selectedReceiverOrganization: OrganizationEntity? = null

    // -------------------------------------------------
    // TRUCK MASTER
    // -------------------------------------------------

    private var truckMasterList: List<TruckMasterEntity> = emptyList()
    private var truckTypeList: List<String> = emptyList()

    // -------------------------------------------------
    // VIEWMODEL
    // -------------------------------------------------

    private val viewModel: DispatchViewModel by viewModels {
        DispatchViewModelFactory(
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

        setContentView(R.layout.activity_create_header_dispatch)

        sessionManager = SessionManager(this)
        db = AppDatabase.getInstance(applicationContext)

        initViews()
        setupClickListeners()
        setupReceiverOrganizationDropdown()
        setupTruckTypeDropdown()
        loadOrganizations()
        observeViewModel()
    }

    // -------------------------------------------------
    // INITIALIZE VIEWS
    // -------------------------------------------------

    private fun initViews() {

        btnBack = findViewById(R.id.btn_back)

        txtSenderOrg = findViewById(R.id.txtSenderOrg)
        tvReceiverOrg = findViewById(R.id.tvReceiverOrg)
        tvTruckType = findViewById(R.id.tvTruckType)

        spTransporterName = findViewById(R.id.spTransporterName)

        rb20Weighment = findViewById(R.id.rb20Weighment)
        rb100Weighment = findViewById(R.id.rb100Weighment)

        edtTruckNo = findViewById(R.id.edtTruckNo)
        edtRcNo = findViewById(R.id.edtRcNo)
        edtDriverName = findViewById(R.id.edtDriverName)
        edtDriverLicense = findViewById(R.id.edtDriverLicense)
        edtFreightCharges = findViewById(R.id.edtFreightCharges)

        loadSenderOrganization()
    }

    // -------------------------------------------------
    // CLICK LISTENERS
    // -------------------------------------------------

    private fun setupClickListeners() {

        btnBack.setOnClickListener {
            finish()
        }
    }

    // -------------------------------------------------
    // LOAD ORGANIZATIONS
    // -------------------------------------------------

    private fun loadOrganizations() {

        ioExecutor.execute {

            val organizations =
                db.organizationDao().getAllOrganizations()

            runOnUiThread {

                allOrganizations = organizations

                if (allOrganizations.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No organizations synced yet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // -------------------------------------------------
    // SENDER ORGANIZATION
    // -------------------------------------------------

    private fun loadSenderOrganization() {

        val organizationCode =
            sessionManager.getOrganizationCode()

        if (organizationCode.isNullOrBlank()) {

            Toast.makeText(
                this,
                "Organization not found in session",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        ioExecutor.execute {

            val organization =
                db.organizationDao()
                    .getByCode(organizationCode)

            runOnUiThread {

                selectedSenderOrganization = organization

                if (organization != null) {

                    txtSenderOrg.text =
                        "${organization.organizationCode} - ${organization.organizationName ?: ""}"

                } else {

                    txtSenderOrg.text =
                        organizationCode
                }
            }
        }
    }

    // -------------------------------------------------
    // RECEIVER ORGANIZATION
    // -------------------------------------------------

    private fun setupReceiverOrganizationDropdown() {

        findViewById<View>(R.id.dropdownReceiverOrg)
            .setOnClickListener {

                if (allOrganizations.isEmpty()) {

                    Toast.makeText(
                        this,
                        "No organizations found",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                val senderCode =
                    selectedSenderOrganization?.organizationCode

                val receiverOrganizations =
                    allOrganizations.filter {
                        it.organizationCode != senderCode
                    }

                if (receiverOrganizations.isEmpty()) {

                    Toast.makeText(
                        this,
                        "No receiver organizations found",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                val names = receiverOrganizations.distinctBy { it.organizationCode }.map {
                    "${it.organizationCode} - ${it.organizationName ?: ""}"
                }

                SearchableSpinnerDialog(
                    this,
                    "Select Receiver Organization",
                    names
                ) { which ->

                    selectedReceiverOrganization =
                        receiverOrganizations[which]

                    tvReceiverOrg.text =
                        names[which]

                    tvTruckType.text =
                        "Select Type of Truck"

                    truckTypeList = emptyList()
                    truckMasterList = emptyList()

                    loadTruckTypes()
                }.show()
            }
    }

    // -------------------------------------------------
    // TRUCK MASTER
    // -------------------------------------------------

    private fun setupTruckTypeDropdown() {

        findViewById<View>(R.id.dropdownTruckType)
            .setOnClickListener {

                if (selectedReceiverOrganization == null) {

                    Toast.makeText(
                        this,
                        "Please select receiver organization first",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                if (truckTypeList.isEmpty()) {

                    Toast.makeText(
                        this,
                        "No truck types available",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                SearchableSpinnerDialog(
                    this,
                    "Select Type of Truck",
                    truckTypeList
                ) { which ->

                    tvTruckType.text =
                        truckTypeList[which]

                }.show()
            }
    }

    private fun loadTruckTypes() {

        val sender =
            selectedSenderOrganization

        val receiver =
            selectedReceiverOrganization

        if (sender == null || receiver == null) {
            return
        }

        val fromOrgn =
            "${sender.organizationCode}-${sender.organizationName ?: ""}"

        val toOrgn =
            "${receiver.organizationCode}-${receiver.organizationName ?: ""}"

        viewModel.loadTruckTypes(
            fromOrgn = fromOrgn,
            toOrgn = toOrgn
        )
    }

    // -------------------------------------------------
    // VIEWMODEL OBSERVER
    // -------------------------------------------------

    private fun observeViewModel() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {

                    viewModel.truckMasterState.collect { state ->

                        when (state) {

                            is TruckMasterState.Idle -> {
                                // Nothing to do
                            }

                            is TruckMasterState.Loading -> {
                                // Truck master loading
                            }

                            is TruckMasterState.Success -> {

                                truckMasterList =
                                    state.trucks

                                setTruckTypes(
                                    truckMasterList
                                )
                            }

                            is TruckMasterState.Error -> {

                                truckMasterList =
                                    emptyList()

                                setTruckTypes(emptyList())

                                Toast.makeText(
                                    this@CreateHeaderDispatch,
                                    state.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------
    // SET TRUCK TYPES
    // -------------------------------------------------

    private fun setTruckTypes(
        trucks: List<TruckMasterEntity>
    ) {

        val types = trucks
            .mapNotNull {
                it.truckType
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()
            .toMutableList()

        if (!types.contains("Others")) {
            types.add("Others")
        }

        truckTypeList = types

        tvTruckType.text =
            "Select Type of Truck"
    }

    // -------------------------------------------------
    // WEIGHMENT TYPE
    // -------------------------------------------------

    private fun getSelectedWeighmentType(): String? {
        return when {
            rb20Weighment.isChecked -> "TWT"
            rb100Weighment.isChecked -> "HND"
            else -> null
        }
    }

    // -------------------------------------------------
    // CLEANUP
    // -------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}