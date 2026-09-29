package com.united.digitaldispatch.Dispatch

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.CreateDispatchState
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiverOrganizationsState
import com.united.digitaldispatch.Apiservice.viewmodel.TruckMasterState
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderRequest
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.TransporterEntity
import com.united.digitaldispatch.data.local.entity.TruckMasterEntity
import com.united.digitaldispatch.utils.ConfirmDispatchDialog
import com.united.digitaldispatch.utils.SearchableSpinnerDialog
import com.united.digitaldispatch.utils.SessionManager
import com.united.digitaldispatch.utils.TimeUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CreateHeaderDispatch : AppCompatActivity() {

    private companion object {

        // Same limits as the old app
        const val MAX_FREIGHT_PER_KG = 15.0
        const val MAX_FREIGHT_PER_TRUCK = 100000.0

        // TODO: must match TimeUtils.simpleCompetitionYearMonthDateFormat of the OLD app
        // (used for the shipment number). Placeholder until confirmed.
        const val SHIPMENT_DATE_PATTERN = "yyMMdd"

        // TODO: must match TimeUtils.simpleYearMonthDateFormat of the OLD app / backend
        const val SENDER_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    }

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
    private lateinit var btnSave: AppCompatButton
    private lateinit var btnClear: AppCompatButton

    private lateinit var txtSenderOrg: TextView
    private lateinit var tvReceiverOrg: TextView
    private lateinit var tvTruckType: TextView
    private lateinit var tvFreightUnit: TextView

    private var transporters: List<TransporterEntity> = emptyList()
    private var selectedTransporter: TransporterEntity? = null
    private lateinit var tvTransporter: TextView

    private lateinit var rgWeighment: RadioGroup
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

    private var receiverOrganizations: List<String> = emptyList()

    private var selectedSenderOrganization: OrganizationEntity? = null
    private var selectedReceiverOrganization: String? = null

    // -------------------------------------------------
    // TRUCK MASTER
    // -------------------------------------------------

    private var truckMasterList: List<TruckMasterEntity> = emptyList()
    private var truckTypeList: List<String> = emptyList()
    private var selectedTruckType: String? = null

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
        setupTransporterDropdown()
        loadOrganizations()
        loadTransporters()
        observeViewModel()

        viewModel.loadReceiverOrganizations()
    }

    // -------------------------------------------------
    // INITIALIZE VIEWS
    // -------------------------------------------------

    private fun initViews() {

        btnBack = findViewById(R.id.btn_back)
        btnSave = findViewById(R.id.btnSave)
        btnClear = findViewById(R.id.btnClear)

        txtSenderOrg = findViewById(R.id.txtSenderOrg)
        tvReceiverOrg = findViewById(R.id.tvReceiverOrg)
        tvTruckType = findViewById(R.id.tvTruckType)
        tvFreightUnit = findViewById(R.id.tvFreightUnit)

        tvTransporter = findViewById(R.id.tvTransporter)

        rgWeighment = findViewById(R.id.rgWeighment)
        rb20Weighment = findViewById(R.id.rb20Weighment)
        rb100Weighment = findViewById(R.id.rb100Weighment)

        edtTruckNo = findViewById(R.id.edtTruckNo)
        edtRcNo = findViewById(R.id.edtRcNo)
        edtDriverName = findViewById(R.id.edtDriverName)
        edtDriverLicense = findViewById(R.id.edtDriverLicense)
        edtFreightCharges = findViewById(R.id.edtFreightCharges)

        // Force capital letters on every keystroke / paste
        val allCaps = arrayOf<InputFilter>(InputFilter.AllCaps())
        edtTruckNo.filters = allCaps
        edtRcNo.filters = allCaps
        edtDriverName.filters = allCaps
        edtDriverLicense.filters = allCaps

        // Field order + locked until receiver org is chosen
        setupFieldFlow()
        setDetailFieldsEnabled(false)

        loadSenderOrganization()
    }

    // -------------------------------------------------
    // CLICK LISTENERS
    // -------------------------------------------------

    private fun setupClickListeners() {

        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            onSaveClicked()
        }

        btnClear.setOnClickListener {
            clearForm()
        }
    }

    // -------------------------------------------------
    // FIELD FLOW / KEYBOARD
    // -------------------------------------------------

    private fun setupFieldFlow() {

        edtTruckNo.imeOptions = EditorInfo.IME_ACTION_NEXT
        edtRcNo.imeOptions = EditorInfo.IME_ACTION_NEXT
        edtDriverName.imeOptions = EditorInfo.IME_ACTION_NEXT
        edtDriverLicense.imeOptions = EditorInfo.IME_ACTION_DONE

        edtTruckNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtRcNo.requestFocus()
                true
            } else false
        }

        edtRcNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtDriverName.requestFocus()
                true
            } else false
        }

        edtDriverName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtDriverLicense.requestFocus()
                true
            } else false
        }

        edtDriverLicense.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v)
                showTransporterDialog()
                true
            } else false
        }
    }

    private fun setDetailFieldsEnabled(enabled: Boolean) {
        edtTruckNo.isEnabled = enabled
        edtRcNo.isEnabled = enabled
        edtDriverName.isEnabled = enabled
        edtDriverLicense.isEnabled = enabled
    }

    private fun focusAndShowKeyboard(view: EditText) {
        // small delay so the dialog finishes closing first
        view.postDelayed({
            view.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
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
                    showToast("No organizations synced yet")
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
            showToast("Organization not found in session")
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

                // Weighment type must be chosen first
                if (getSelectedWeighmentType() == null) {
                    showToast("Please select weighment type first")
                    return@setOnClickListener
                }

                if (receiverOrganizations.isEmpty()) {
                    showToast("No receiver organizations available")
                    return@setOnClickListener
                }

                SearchableSpinnerDialog(
                    this,
                    "Select Receiver Organization",
                    receiverOrganizations
                ) { which ->

                    val selected = receiverOrganizations[which]

                    // Sender and receiver must be different (old app rule)
                    val senderCode = selectedSenderOrganization?.organizationCode
                    if (senderCode != null && extractOrgCode(selected) == senderCode) {
                        showToast("Change the receiver organization")
                        return@SearchableSpinnerDialog
                    }

                    selectedReceiverOrganization = selected
                    tvReceiverOrg.text = selected

                    truckTypeList = emptyList()
                    truckMasterList = emptyList()
                    resetTruckTypeSelection()

                    loadTruckTypes()

                    // unlock the fields and move to Truck No
                    setDetailFieldsEnabled(true)
                    focusAndShowKeyboard(edtTruckNo)

                }.show()
            }
    }

    // -------------------------------------------------
    // TRANSPORTER
    // -------------------------------------------------

    private fun setupTransporterDropdown() {

        findViewById<View>(R.id.dropdownTransporter)
            .setOnClickListener {
                showTransporterDialog()
            }
    }

    private fun showTransporterDialog() {

        if (selectedReceiverOrganization == null) {
            showToast("Please select receiver organization first")
            return
        }

        if (transporters.isEmpty()) {
            showToast("No transporters available")
            return
        }

        val transporterNames = transporters.map {
            "${it.transporterCode} - ${it.transporterName ?: ""}"
        }

        SearchableSpinnerDialog(
            this,
            "Select Transporter",
            transporterNames
        ) { which ->

            selectedTransporter = transporters[which]
            tvTransporter.text = transporterNames[which]

            // next: truck type
            tvTransporter.postDelayed({ showTruckTypeDialog() }, 250)

        }.show()
    }

    private fun loadTransporters() {

        ioExecutor.execute {

            val transporterList =
                db.transporterDao().getTransporters()

            runOnUiThread {

                transporters = transporterList
            }
        }
    }

    // -------------------------------------------------
    // TRUCK TYPE
    // -------------------------------------------------

    private fun setupTruckTypeDropdown() {

        findViewById<View>(R.id.dropdownTruckType)
            .setOnClickListener {
                showTruckTypeDialog()
            }
    }

    private fun showTruckTypeDialog() {

        if (selectedReceiverOrganization == null) {
            showToast("Please select receiver organization first")
            return
        }

        if (truckTypeList.isEmpty()) {
            showToast("No truck types available")
            return
        }

        SearchableSpinnerDialog(
            this,
            "Select Type of Truck",
            truckTypeList
        ) { which ->

            val type = truckTypeList[which]

            selectedTruckType = type
            tvTruckType.text = type

            applyFreightForTruckType(type)

            if (isOthers(type)) {
                // manual freight entry
                focusAndShowKeyboard(edtFreightCharges)
            } else {
                hideKeyboard(edtFreightCharges)
            }

        }.show()
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

        viewModel.loadTruckTypes(
            fromOrgn = fromOrgn,
            toOrgn = receiver
        )
    }

    // -------------------------------------------------
    // VIEWMODEL OBSERVER
    // -------------------------------------------------

    private fun observeViewModel() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.receiverOrganizationsState.collect { state ->

                        when (state) {

                            is ReceiverOrganizationsState.Idle -> {
                            }

                            is ReceiverOrganizationsState.Loading -> {
                            }

                            is ReceiverOrganizationsState.Success -> {
                                receiverOrganizations = state.organizations
                            }

                            is ReceiverOrganizationsState.Error -> {

                                receiverOrganizations = emptyList()

                                showToast(state.message)
                            }
                        }
                    }
                }

                launch {

                    viewModel.truckMasterState.collect { state ->

                        when (state) {

                            is TruckMasterState.Idle -> {
                            }

                            is TruckMasterState.Loading -> {
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

                                showToast(state.message)
                            }
                        }
                    }
                }

                launch {

                    viewModel.createDispatchState.collect { state ->

                        when (state) {

                            is CreateDispatchState.Idle -> {
                                btnSave.isEnabled = true
                            }

                            is CreateDispatchState.Loading -> {
                                btnSave.isEnabled = false
                            }

                            is CreateDispatchState.Success -> {
                                btnSave.isEnabled = true
                                showToast("Dispatch header created")

                                // TODO: like the old app -> save header id / truck no
                                // locally and open the dispatch details screen here.
                                clearForm()
                                viewModel.resetCreateDispatchState()
                            }

                            is CreateDispatchState.Error -> {
                                btnSave.isEnabled = true
                                showToast(state.message)
                                viewModel.resetCreateDispatchState()
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

        resetTruckTypeSelection()
    }

    // -------------------------------------------------
    // FREIGHT
    // -------------------------------------------------

    private fun isOthers(type: String?): Boolean =
        type.equals("Others", ignoreCase = true)

    private fun applyFreightForTruckType(type: String) {

        if (isOthers(type)) {

            // Manual entry -> UOM = KG
            edtFreightCharges.setText("")
            edtFreightCharges.isEnabled = true
            tvFreightUnit.text = "/Kg"

        } else {

            // Truck based freight -> UOM = TRUCK
            val freight = truckMasterList
                .firstOrNull { it.truckType == type }
                ?.freightValue

            edtFreightCharges.setText(freight?.toString() ?: "")
            edtFreightCharges.isEnabled = false
            tvFreightUnit.text = "/Truck"
        }
    }

    private fun resetTruckTypeSelection() {
        selectedTruckType = null
        tvTruckType.text = "Select Type of Truck"
        edtFreightCharges.setText("")
        edtFreightCharges.isEnabled = true
        tvFreightUnit.text = "/Kg"
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
    // HELPERS
    // -------------------------------------------------

    private fun extractOrgCode(display: String): String =
        display.substringBefore("-").trim()

    private fun isWarehouse(orgCode: String): Boolean =
        allOrganizations
            .firstOrNull { it.organizationCode == orgCode }
            ?.organizationType
            .equals("WH", ignoreCase = true)

    /**
     * Old app: shipmentNo = senderOrgCode + receiverOrgCode + date
     * (txtHeaderId). Date pattern must match the old TimeUtils format.
     */
    private fun generateShipmentNo(senderCode: String, receiverCode: String): String =
        senderCode + receiverCode + TimeUtils.shipmentDatePart()

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun fieldError(field: EditText, message: String) {
        field.error = message
        field.requestFocus()
    }

    // -------------------------------------------------
    // SAVE
    // -------------------------------------------------

    private fun onSaveClicked() {

        val sender = selectedSenderOrganization
            ?: return showToast("Sender organization not loaded")

        val weighmentType = getSelectedWeighmentType()
            ?: return showToast("Please select weighment type")

        val receiverDisplay = selectedReceiverOrganization
            ?: return showToast("Please select receiver organization")

        val senderCode = sender.organizationCode
        val receiverCode = extractOrgCode(receiverDisplay)

        if (receiverCode.isEmpty()) {
            return showToast("Invalid receiver organization")
        }

        if (senderCode == receiverCode) {
            return showToast("Change the receiver organization")
        }

        val truckNo = edtTruckNo.text.toString().trim().uppercase()
        val rcNo = edtRcNo.text.toString().trim().uppercase()
        val driverName = edtDriverName.text.toString().trim().uppercase()
        val license = edtDriverLicense.text.toString().trim().uppercase()

        if (truckNo.isEmpty()) return fieldError(edtTruckNo, "Enter the truck number")
        if (rcNo.isEmpty()) return fieldError(edtRcNo, "Enter the RC number")
        if (driverName.isEmpty()) return fieldError(edtDriverName, "Enter the driver name")
        if (license.isEmpty()) return fieldError(edtDriverLicense, "Enter the driver license")

        val transporter = selectedTransporter
            ?: return showToast("Please select transporter")

        if (transporter.transporterCode.isBlank()) {
            return showToast("Please select transporter")
        }

        val truckType = selectedTruckType
            ?: return showToast("Please select type of truck")

        // ---- Freight + UOM ----
        val others = isOthers(truckType)

        val freight: Double? = if (others) {
            edtFreightCharges.text.toString().trim().toDoubleOrNull()
        } else {
            truckMasterList
                .firstOrNull { it.truckType == truckType }
                ?.freightValue
                ?.toString()
                ?.toDoubleOrNull()
        }

        if (freight == null) {
            return if (others) {
                fieldError(edtFreightCharges, "Enter valid freight charges")
            } else {
                showToast("Freight not found for selected truck type")
            }
        }

        if (freight <= 0.0) {
            return if (others) {
                fieldError(edtFreightCharges, "Freight must be greater than 0")
            } else {
                showToast("Freight not found for selected truck type")
            }
        }

        if (others && freight > MAX_FREIGHT_PER_KG) {
            return fieldError(
                edtFreightCharges,
                "Freight per Kg is allowed only up to ${MAX_FREIGHT_PER_KG.toInt()}"
            )
        }

        if (!others && freight > MAX_FREIGHT_PER_TRUCK) {
            return showToast(
                "Freight per truck is allowed only up to ${MAX_FREIGHT_PER_TRUCK.toInt()}"
            )
        }

        val uom = if (others) "KG" else "TRUCK"

        val isWms =
            if (isWarehouse(senderCode) || isWarehouse(receiverCode)) "Y" else "N"

        val userId = sessionManager.getUserId()
        if (userId.isNullOrBlank()) {
            return showToast("User not found in session. Please login again")
        }

        val request = CreateDispatchHeaderRequest(
            shipmentNo = generateShipmentNo(senderCode, receiverCode),
            senderOrgnCode = senderCode,
            receiverOrgnCode = receiverCode,
            senderDate = TimeUtils.senderDate(),
            sentBy = userId,
            senderTruckNo = truckNo,
            rcNo = rcNo,
            driverName = driverName,
            drivingLicenceNo = license,
            // Old app sent the transporter CODE here
            transportName = transporter.transporterCode,
            typeOfTruck = truckType,
            frieghtCharges = freight,
            uom = uom,
            status = "INT",
            attribute2 = senderCode,
            attribute3 = receiverCode,
            isWmsShipment = isWms,
            attribute4 = "",
            weighmentType = weighmentType
        )

        showConfirmDialog(request, txtSenderOrg.text.toString(), receiverDisplay)
    }

    // -------------------------------------------------
    // CONFIRM DIALOG (same idea as the old app)
    // -------------------------------------------------

    private fun showConfirmDialog(
        request: CreateDispatchHeaderRequest,
        senderDisplay: String,
        receiverDisplay: String
    ) {

        val unit = if (request.uom == "KG") "/Kg" else "/Truck"

        val weighmentText =
            if (request.weighmentType == "TWT") "20% Weighment" else "100% Weighment"

        val rows = listOf(
            "Sender" to senderDisplay,
            "Receiver" to receiverDisplay,
            "Weighment" to weighmentText,
            "Truck No" to request.senderTruckNo.orEmpty(),
            "RC No" to request.rcNo.orEmpty(),
            "Driver" to request.driverName.orEmpty(),
            "License" to request.drivingLicenceNo.orEmpty(),
            "Transporter" to tvTransporter.text.toString(),
            "Truck Type" to request.typeOfTruck.orEmpty()
        )

        ConfirmDispatchDialog(
            activity = this,
            shipmentNo = request.shipmentNo.orEmpty(),
            rows = rows,
            freightText = "${request.frieghtCharges} $unit"
        ) {
            viewModel.createDispatchHeader(request)
        }.show()
    }

    // -------------------------------------------------
    // CLEAR (everything except sender org)
    // -------------------------------------------------

    private fun clearForm() {

        rgWeighment.clearCheck()

        selectedReceiverOrganization = null
        tvReceiverOrg.text = "Select Receiver Organization"

        truckTypeList = emptyList()
        truckMasterList = emptyList()
        resetTruckTypeSelection()

        edtTruckNo.setText("")
        edtRcNo.setText("")
        edtDriverName.setText("")
        edtDriverLicense.setText("")

        // lock the fields again until a receiver org is chosen
        setDetailFieldsEnabled(false)
        hideKeyboard(edtTruckNo)

        selectedTransporter = null
        tvTransporter.text = "Select Transporter"
    }

    // -------------------------------------------------
    // CLEANUP
    // -------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}