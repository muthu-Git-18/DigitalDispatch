package com.united.digitaldispatch.PSWDispatch

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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiverOrganizationsState
import com.united.digitaldispatch.Apiservice.viewmodel.TruckMasterState
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderRequest
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
import com.united.digitaldispatch.Apiservice.viewmodel.CreatePSWDispatchState
import java.util.concurrent.Executors

class CreateHeaderPSWDispatch : AppCompatActivity() {

    private val pswViewModel: PswDispatchViewModel by viewModels {
        PswDispatchViewModelFactory(
            MasterDataRepository(
                ApiClient.instance,
                AppDatabase.getInstance(applicationContext)
            )
        )
    }

    private companion object {

        const val MAX_FREIGHT_PER_KG = 15.0
        const val MAX_FREIGHT_PER_TRUCK = 100000.0
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
    // TRANSPORTER
    // -------------------------------------------------

    private var transporters: List<TransporterEntity> = emptyList()
    private var selectedTransporter: TransporterEntity? = null

    // -------------------------------------------------
    // TRUCK MASTER
    // -------------------------------------------------

    private var truckMasterList: List<TruckMasterEntity> = emptyList()
    private var truckTypeList: List<String> = emptyList()
    private var selectedTruckType: String? = null

    // -------------------------------------------------
    // MASTER DATA VIEWMODEL
    // -------------------------------------------------

    /*
     * Same ViewModel used by senior's Dispatch screen.
     *
     * We use this only for:
     * - Receiver organizations
     * - Truck master
     *
     * PSW creation itself uses PswDispatchViewModel below.
     */
    private val masterViewModel: DispatchViewModel by viewModels {

        DispatchViewModelFactory(
            MasterDataRepository(
                ApiClient.instance,
                AppDatabase.getInstance(applicationContext)
            )
        )
    }

    // -------------------------------------------------
    // PSW VIEWMODEL
    // -------------------------------------------------

    // -------------------------------------------------
    // ON CREATE
    // -------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_create_header_pswdispatch)

        sessionManager = SessionManager(this)
        db = AppDatabase.getInstance(applicationContext)

        initViews()

        setupClickListeners()

        setupFieldFlow()

        setupReceiverOrganizationDropdown()

        setupTransporterDropdown()

        setupTruckTypeDropdown()

        loadOrganizations()

        loadTransporters()

        observeViewModels()

        masterViewModel.loadReceiverOrganizations()
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

        /*
         * Same as senior's Dispatch screen.
         * Force capital letters.
         */
        val allCaps = arrayOf<InputFilter>(
            InputFilter.AllCaps()
        )

        edtTruckNo.filters = allCaps
        edtRcNo.filters = allCaps
        edtDriverName.filters = allCaps
        edtDriverLicense.filters = allCaps

        /*
         * Detail fields stay locked until
         * receiver organization is selected.
         */
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

        edtTruckNo.imeOptions =
            EditorInfo.IME_ACTION_NEXT

        edtRcNo.imeOptions =
            EditorInfo.IME_ACTION_NEXT

        edtDriverName.imeOptions =
            EditorInfo.IME_ACTION_NEXT

        edtDriverLicense.imeOptions =
            EditorInfo.IME_ACTION_DONE

        edtTruckNo.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_NEXT) {

                edtRcNo.requestFocus()

                true

            } else {
                false
            }
        }

        edtRcNo.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_NEXT) {

                edtDriverName.requestFocus()

                true

            } else {
                false
            }
        }

        edtDriverName.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_NEXT) {

                edtDriverLicense.requestFocus()

                true

            } else {
                false
            }
        }

        edtDriverLicense.setOnEditorActionListener { view, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_DONE) {

                hideKeyboard(view)

                showTransporterDialog()

                true

            } else {
                false
            }
        }
    }

    private fun setDetailFieldsEnabled(
        enabled: Boolean
    ) {

        edtTruckNo.isEnabled = enabled
        edtRcNo.isEnabled = enabled
        edtDriverName.isEnabled = enabled
        edtDriverLicense.isEnabled = enabled
    }

    private fun focusAndShowKeyboard(
        view: EditText
    ) {

        view.postDelayed({

            view.requestFocus()

            val imm =
                getSystemService(INPUT_METHOD_SERVICE)
                        as InputMethodManager

            imm.showSoftInput(
                view,
                InputMethodManager.SHOW_IMPLICIT
            )

        }, 250)
    }

    private fun hideKeyboard(view: View) {

        val imm =
            getSystemService(INPUT_METHOD_SERVICE)
                    as InputMethodManager

        imm.hideSoftInputFromWindow(
            view.windowToken,
            0
        )
    }

    // -------------------------------------------------
    // LOAD ORGANIZATIONS
    // -------------------------------------------------

    private fun loadOrganizations() {

        ioExecutor.execute {

            val organizations =
                db.organizationDao()
                    .getAllOrganizations()

            runOnUiThread {

                allOrganizations = organizations

                if (allOrganizations.isEmpty()) {

                    showToast(
                        "No organizations synced yet"
                    )
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

            showToast(
                "Organization not found in session"
            )

            return
        }

        ioExecutor.execute {

            val organization =
                db.organizationDao()
                    .getByCode(organizationCode)

            runOnUiThread {

                selectedSenderOrganization =
                    organization

                if (organization != null) {

                    txtSenderOrg.text =
                        "${organization.organizationCode} - " +
                                "${organization.organizationName ?: ""}"

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

        findViewById<View>(
            R.id.dropdownReceiverOrg
        ).setOnClickListener {

            /*
             * Same validation as senior's Dispatch.
             */
            if (getSelectedWeighmentType() == null) {

                showToast(
                    "Please select weighment type first"
                )

                return@setOnClickListener
            }

            if (receiverOrganizations.isEmpty()) {

                showToast(
                    "No receiver organizations available"
                )

                return@setOnClickListener
            }

            SearchableSpinnerDialog(
                this,
                "Select Receiver Organization",
                receiverOrganizations
            ) { which ->

                val selected =
                    receiverOrganizations[which]

                val senderCode =
                    selectedSenderOrganization
                        ?.organizationCode

                /*
                 * Sender and receiver cannot be same.
                 */
                if (
                    senderCode != null &&
                    extractOrgCode(selected) == senderCode
                ) {

                    showToast(
                        "Change the receiver organization"
                    )

                    return@SearchableSpinnerDialog
                }

                selectedReceiverOrganization =
                    selected

                tvReceiverOrg.text =
                    selected

                /*
                 * Reset truck information whenever
                 * receiver changes.
                 */
                truckTypeList = emptyList()
                truckMasterList = emptyList()

                resetTruckTypeSelection()

                /*
                 * Load truck master based on
                 * sender -> receiver.
                 */
                loadTruckTypes()

                /*
                 * Unlock vehicle fields.
                 */
                setDetailFieldsEnabled(true)

                focusAndShowKeyboard(
                    edtTruckNo
                )

            }.show()
        }
    }

    // -------------------------------------------------
    // TRANSPORTER
    // -------------------------------------------------

    private fun setupTransporterDropdown() {

        findViewById<View>(
            R.id.dropdownTransporter
        ).setOnClickListener {

            showTransporterDialog()
        }
    }

    private fun showTransporterDialog() {

        if (selectedReceiverOrganization == null) {

            showToast(
                "Please select receiver organization first"
            )

            return
        }

        if (transporters.isEmpty()) {

            showToast(
                "No transporters available"
            )

            return
        }

        val transporterNames =
            transporters.map {

                "${it.transporterCode} - " +
                        "${it.transporterName ?: ""}"
            }

        SearchableSpinnerDialog(
            this,
            "Select Transporter",
            transporterNames
        ) { which ->

            selectedTransporter =
                transporters[which]

            tvTransporter.text =
                transporterNames[which]

            /*
             * Same flow as senior screen:
             * transporter -> truck type.
             */
            tvTransporter.postDelayed({

                showTruckTypeDialog()

            }, 250)

        }.show()
    }

    private fun loadTransporters() {

        ioExecutor.execute {

            val transporterList =
                db.transporterDao()
                    .getTransporters()

            runOnUiThread {

                transporters =
                    transporterList
            }
        }
    }

    // -------------------------------------------------
    // TRUCK TYPE
    // -------------------------------------------------

    private fun setupTruckTypeDropdown() {

        findViewById<View>(
            R.id.dropdownTruckType
        ).setOnClickListener {

            showTruckTypeDialog()
        }
    }

    private fun showTruckTypeDialog() {

        if (selectedReceiverOrganization == null) {

            showToast(
                "Please select receiver organization first"
            )

            return
        }

        if (truckTypeList.isEmpty()) {

            showToast(
                "No truck types available"
            )

            return
        }

        SearchableSpinnerDialog(
            this,
            "Select Type of Truck",
            truckTypeList
        ) { which ->

            val type =
                truckTypeList[which]

            selectedTruckType =
                type

            tvTruckType.text =
                type

            applyFreightForTruckType(type)

            if (isOthers(type)) {

                focusAndShowKeyboard(
                    edtFreightCharges
                )

            } else {

                hideKeyboard(
                    edtFreightCharges
                )
            }

        }.show()
    }

    private fun loadTruckTypes() {

        val sender =
            selectedSenderOrganization

        val receiver =
            selectedReceiverOrganization

        if (
            sender == null ||
            receiver == null
        ) {
            return
        }

        val fromOrgn =
            "${sender.organizationCode}-" +
                    "${sender.organizationName ?: ""}"

        /*
         * Same truck-master call used by senior.
         */
        masterViewModel.loadTruckTypes(
            fromOrgn = fromOrgn,
            toOrgn = receiver
        )
    }

    // -------------------------------------------------
    // OBSERVE VIEWMODELS
    // -------------------------------------------------

    private fun observeViewModels() {

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                /*
                 * ---------------------------------------
                 * RECEIVER ORGANIZATIONS
                 * ---------------------------------------
                 */

                launch {

                    masterViewModel
                        .receiverOrganizationsState
                        .collect { state ->

                            when (state) {

                                is ReceiverOrganizationsState.Idle -> {
                                }

                                is ReceiverOrganizationsState.Loading -> {
                                }

                                is ReceiverOrganizationsState.Success -> {

                                    receiverOrganizations =
                                        state.organizations
                                }

                                is ReceiverOrganizationsState.Error -> {

                                    receiverOrganizations =
                                        emptyList()

                                    showToast(
                                        state.message
                                    )
                                }
                            }
                        }
                }

                /*
                 * ---------------------------------------
                 * TRUCK MASTER
                 * ---------------------------------------
                 */

                launch {

                    masterViewModel
                        .truckMasterState
                        .collect { state ->

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

                                    setTruckTypes(
                                        emptyList()
                                    )

                                    showToast(
                                        state.message
                                    )
                                }
                            }
                        }
                }

                /*
                 * ---------------------------------------
                 * PSW CREATE
                 * ---------------------------------------
                 *
                 * IMPORTANT:
                 * This is PSW ViewModel.
                 */

                launch {

                    pswViewModel
                        .createPswDispatchState
                        .collect { state ->

                            when (state) {
                                is CreatePSWDispatchState.Idle -> {
                                    btnSave.isEnabled =
                                        true
                                }

                                is CreatePSWDispatchState.Loading -> {
                                    btnSave.isEnabled =
                                        false
                                }

                                is CreatePSWDispatchState.Success -> {
                                    btnSave.isEnabled =
                                        true

                                    showToast(
                                        "PSW Dispatch header created"
                                    )

                                    clearForm()

                                    pswViewModel
                                        .resetCreatePswDispatchState()
                                }

                                is CreatePSWDispatchState.Error -> {

                                    btnSave.isEnabled =
                                        true

                                    showToast(
                                        state.message
                                    )

                                    pswViewModel
                                        .resetCreatePswDispatchState()
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

        val types =
            trucks
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

        truckTypeList =
            types

        resetTruckTypeSelection()
    }

    // -------------------------------------------------
    // FREIGHT
    // -------------------------------------------------

    private fun isOthers(
        type: String?
    ): Boolean =
        type.equals(
            "Others",
            ignoreCase = true
        )

    private fun applyFreightForTruckType(
        type: String
    ) {

        if (isOthers(type)) {

            /*
             * Manual freight.
             * UOM = KG.
             */
            edtFreightCharges.setText("")

            edtFreightCharges.isEnabled =
                true

            tvFreightUnit.text =
                "/Kg"

        } else {

            /*
             * Truck-master freight.
             * UOM = TRUCK.
             */
            val freight =
                truckMasterList
                    .firstOrNull {
                        it.truckType == type
                    }
                    ?.freightValue

            edtFreightCharges.setText(
                freight?.toString() ?: ""
            )

            edtFreightCharges.isEnabled =
                false

            tvFreightUnit.text =
                "/Truck"
        }
    }

    private fun resetTruckTypeSelection() {

        selectedTruckType =
            null

        tvTruckType.text =
            "Select Type of Truck"

        edtFreightCharges.setText("")

        edtFreightCharges.isEnabled =
            true

        tvFreightUnit.text =
            "/Kg"
    }

    // -------------------------------------------------
    // WEIGHMENT
    // -------------------------------------------------

    private fun getSelectedWeighmentType(): String? {

        return when {

            rb20Weighment.isChecked ->
                "TWT"

            rb100Weighment.isChecked ->
                "HND"

            else ->
                null
        }
    }

    // -------------------------------------------------
    // HELPERS
    // -------------------------------------------------

    private fun extractOrgCode(
        display: String
    ): String =
        display
            .substringBefore("-")
            .trim()

    private fun isWarehouse(
        orgCode: String
    ): Boolean =

        allOrganizations
            .firstOrNull {
                it.organizationCode == orgCode
            }
            ?.organizationType
            .equals(
                "WH",
                ignoreCase = true
            )

    private fun generateShipmentNo(
        senderCode: String,
        receiverCode: String
    ): String =

        senderCode +
                receiverCode +
                TimeUtils.shipmentDatePart()

    private fun showToast(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun fieldError(
        field: EditText,
        message: String
    ) {

        field.error =
            message

        field.requestFocus()
    }

    // -------------------------------------------------
    // SAVE
    // -------------------------------------------------

    private fun onSaveClicked() {

        /*
         * -------------------------------------------
         * SENDER
         * -------------------------------------------
         */

        val sender =
            selectedSenderOrganization
                ?: return showToast(
                    "Sender organization not loaded"
                )

        /*
         * -------------------------------------------
         * WEIGHMENT
         * -------------------------------------------
         */

        val weighmentType =
            getSelectedWeighmentType()
                ?: return showToast(
                    "Please select weighment type"
                )

        /*
         * -------------------------------------------
         * RECEIVER
         * -------------------------------------------
         */

        val receiverDisplay =
            selectedReceiverOrganization
                ?: return showToast(
                    "Please select receiver organization"
                )

        val senderCode =
            sender.organizationCode

        val receiverCode =
            extractOrgCode(
                receiverDisplay
            )

        if (receiverCode.isEmpty()) {

            return showToast(
                "Invalid receiver organization"
            )
        }

        /*
         * Sender and receiver must be different.
         */
        if (senderCode == receiverCode) {

            return showToast(
                "Change the receiver organization"
            )
        }

        /*
         * -------------------------------------------
         * VEHICLE / DRIVER
         * -------------------------------------------
         */

        val truckNo =
            edtTruckNo.text
                .toString()
                .trim()
                .uppercase()

        val rcNo =
            edtRcNo.text
                .toString()
                .trim()
                .uppercase()

        val driverName =
            edtDriverName.text
                .toString()
                .trim()
                .uppercase()

        val license =
            edtDriverLicense.text
                .toString()
                .trim()
                .uppercase()

        if (truckNo.isEmpty()) {

            return fieldError(
                edtTruckNo,
                "Enter the truck number"
            )
        }

        if (rcNo.isEmpty()) {

            return fieldError(
                edtRcNo,
                "Enter the RC number"
            )
        }

        if (driverName.isEmpty()) {

            return fieldError(
                edtDriverName,
                "Enter the driver name"
            )
        }

        if (license.isEmpty()) {

            return fieldError(
                edtDriverLicense,
                "Enter the driver license"
            )
        }

        /*
         * -------------------------------------------
         * TRANSPORTER
         * -------------------------------------------
         */

        val transporter =
            selectedTransporter
                ?: return showToast(
                    "Please select transporter"
                )

        if (transporter.transporterCode.isBlank()) {

            return showToast(
                "Please select transporter"
            )
        }

        /*
         * -------------------------------------------
         * TRUCK TYPE
         * -------------------------------------------
         */

        val truckType =
            selectedTruckType
                ?: return showToast(
                    "Please select type of truck"
                )

        /*
         * -------------------------------------------
         * FREIGHT
         * -------------------------------------------
         */

        val others =
            isOthers(truckType)

        val freight: Double? =

            if (others) {

                /*
                 * User enters freight manually.
                 */
                edtFreightCharges
                    .text
                    .toString()
                    .trim()
                    .toDoubleOrNull()

            } else {

                /*
                 * Freight comes from truck master.
                 */
                truckMasterList
                    .firstOrNull {
                        it.truckType == truckType
                    }
                    ?.freightValue
                    ?.toString()
                    ?.toDoubleOrNull()
            }

        /*
         * Freight must exist.
         */
        if (freight == null) {

            return if (others) {

                fieldError(
                    edtFreightCharges,
                    "Enter valid freight charges"
                )

            } else {

                showToast(
                    "Freight not found for selected truck type"
                )
            }
        }

        /*
         * Freight must be > 0.
         */
        if (freight <= 0.0) {

            return if (others) {

                fieldError(
                    edtFreightCharges,
                    "Freight must be greater than 0"
                )

            } else {

                showToast(
                    "Freight not found for selected truck type"
                )
            }
        }

        /*
         * Manual / KG freight limit.
         */
        if (
            others &&
            freight > MAX_FREIGHT_PER_KG
        ) {

            return fieldError(
                edtFreightCharges,
                "Freight per Kg is allowed only up to " +
                        "${MAX_FREIGHT_PER_KG.toInt()}"
            )
        }

        /*
         * Truck freight limit.
         */
        if (
            !others &&
            freight > MAX_FREIGHT_PER_TRUCK
        ) {

            return showToast(
                "Freight per truck is allowed only up to " +
                        "${MAX_FREIGHT_PER_TRUCK.toInt()}"
            )
        }

        /*
         * -------------------------------------------
         * UOM
         * -------------------------------------------
         */

        val uom =
            if (others) {
                "KG"
            } else {
                "TRUCK"
            }

        /*
         * -------------------------------------------
         * WMS
         * -------------------------------------------
         *
         * Same rule as senior's Dispatch.
         */
        val isWms =

            if (
                isWarehouse(senderCode) ||
                isWarehouse(receiverCode)
            ) {
                "Y"
            } else {
                "N"
            }

        /*
         * -------------------------------------------
         * USER
         * -------------------------------------------
         */

        val userId =
            sessionManager.getUserId()

        if (userId.isNullOrBlank()) {

            return showToast(
                "User not found in session. Please login again"
            )
        }

        /*
         * -------------------------------------------
         * CREATE PSW REQUEST
         * -------------------------------------------
         */

        val request =
            CreatePSWDispatchHeaderRequest(

                shipmentNo =
                    generateShipmentNo(
                        senderCode,
                        receiverCode
                    ),

                senderOrgnCode =
                    senderCode,

                receiverOrgnCode =
                    receiverCode,

                senderDate =
                    TimeUtils.senderDate(),

                sentBy =
                    userId,

                senderTruckNo =
                    truckNo,

                rcNo =
                    rcNo,

                driverName =
                    driverName,

                drivingLicenceNo =
                    license,

                /*
                 * Same as normal Dispatch:
                 * transporter CODE is sent,
                 * not transporter name.
                 */
                transportName =
                    transporter.transporterCode,

                typeOfTruck =
                    truckType,

                frieghtCharges =
                    freight,

                uom =
                    uom,

                status =
                    "INT",

                attribute2 =
                    senderCode,

                attribute3 =
                    receiverCode,

                isWmsShipment =
                    isWms,

                attribute4 =
                    "",

                weighmentType =
                    weighmentType
            )

        /*
         * -------------------------------------------
         * CONFIRM
         * -------------------------------------------
         */

        showConfirmDialog(
            request = request,
            senderDisplay = txtSenderOrg.text.toString(),
            receiverDisplay = receiverDisplay
        )
    }

    // -------------------------------------------------
    // CONFIRM DIALOG
    // -------------------------------------------------

    private fun showConfirmDialog(
        request: CreatePSWDispatchHeaderRequest,
        senderDisplay: String,
        receiverDisplay: String
    ) {

        val unit =
            if (request.uom == "KG") {
                "/Kg"
            } else {
                "/Truck"
            }

        val weighmentText =
            if (request.weighmentType == "TWT") {
                "20% Weighment"
            } else {
                "100% Weighment"
            }

        val rows =
            listOf(

                "Sender" to
                        senderDisplay,

                "Receiver" to
                        receiverDisplay,

                "Weighment" to
                        weighmentText,

                "Truck No" to
                        request.senderTruckNo,

                "RC No" to
                        request.rcNo,

                "Driver" to
                        request.driverName,

                "License" to
                        request.drivingLicenceNo,

                "Transporter" to
                        tvTransporter.text.toString(),

                "Truck Type" to
                        request.typeOfTruck
            )

        ConfirmDispatchDialog(

            activity = this,

            shipmentNo =
                request.shipmentNo,

            rows =
                rows,

            freightText =
                "${request.frieghtCharges} $unit"

        ) {

            /*
             * THIS IS THE IMPORTANT PSW CHANGE.
             *
             * Normal:
             * viewModel.createDispatchHeader(request)
             *
             * PSW:
             * pswViewModel.createPswDispatchHeader(request)
             */
            pswViewModel
                .createPswDispatchHeader(
                    request
                )

        }.show()
    }

    // -------------------------------------------------
    // CLEAR
    // -------------------------------------------------

    private fun clearForm() {

        /*
         * Reset weighment.
         */
        rgWeighment.clearCheck()

        /*
         * Keep sender organization.
         */
        selectedReceiverOrganization =
            null

        tvReceiverOrg.text =
            "Select Receiver Organization"

        /*
         * Reset truck data.
         */
        truckTypeList =
            emptyList()

        truckMasterList =
            emptyList()

        resetTruckTypeSelection()

        /*
         * Reset vehicle details.
         */
        edtTruckNo.setText("")
        edtRcNo.setText("")
        edtDriverName.setText("")
        edtDriverLicense.setText("")

        /*
         * Lock fields again.
         */
        setDetailFieldsEnabled(false)

        hideKeyboard(
            edtTruckNo
        )

        /*
         * Reset transporter.
         */
        selectedTransporter =
            null

        tvTransporter.text =
            "Select Transporter"
    }

    // -------------------------------------------------
    // CLEANUP
    // -------------------------------------------------

    override fun onDestroy() {

        super.onDestroy()

        ioExecutor.shutdown()
    }
}
