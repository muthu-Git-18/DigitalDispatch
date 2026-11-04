package com.united.digitaldispatch.PSWDispatch

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.PswBaleLookupState
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.PswDispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.PswHeaderState
import com.united.digitaldispatch.Dispatch.models.BaleInfo
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.PSWDispatchDetailEntity
import com.united.digitaldispatch.data.local.entity.PswDispatchHeaderEntity
import com.united.digitaldispatch.utils.AppAlertDialog
import com.united.digitaldispatch.utils.DecimalInputFilter
import com.united.digitaldispatch.utils.DeleteConfirmDialog
import com.united.digitaldispatch.utils.SessionManager
import com.united.digitaldispatch.utils.TimeUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PSW Dispatch Details (scan bales of one PSW shipment).
 *
 * Structure = Dispatch Details. Business rules = old PSW app:
 *  - bale number is exactly 31 characters (no other format check)
 *  - dispatch weight max 300
 *  - weight loss more than 1.5% -> only rights "A" may accept
 *  - dispatch weight greater than marked weight is allowed
 *  - to-sub-inventory = from-sub-inventory
 * Weighment type comes from the header:
 *  - TWT (20%)  -> weight checkbox optional (not weighed = marked weight)
 *  - HND (100%) -> weight checkbox compulsory (ticked + locked)
 *
 * Saved on the device first (PENDING). Upload / return sync / complete come next.
 */
class PSWDispatchDetails : AppCompatActivity() {

    private companion object {
        const val TWT = "TWT"
        const val HND = "HND"

        const val BALE_LENGTH = 31
        const val MAX_WEIGHT = 300.0
        const val WEIGHT_LOSS_PERCENT = 1.5

        val COLOR_COUNT_OK = Color.parseColor("#2E7D32")       // green
        val COLOR_COUNT_PENDING = Color.parseColor("#C0392B")  // red
    }

    private lateinit var sessionManager: SessionManager

    // views
    private lateinit var txtShipmentNo: TextView
    private lateinit var txtTruckNo: TextView
    private lateinit var txtLotNo: TextView
    private lateinit var txtMrkdWeight: TextView
    private lateinit var txtGrade: TextView
    private lateinit var txtSubInvCode: TextView
    private lateinit var txtRate: TextView
    private lateinit var txtCount: TextView
    private lateinit var txtNote: TextView

    private lateinit var edtBaleNo: EditText
    private lateinit var edtWeight: EditText
    private lateinit var chkWeight: CheckBox

    private lateinit var btnSave: AppCompatButton
    private lateinit var btnClear: AppCompatButton
    private lateinit var btnDelete: AppCompatButton
    private lateinit var btnGradewise: AppCompatButton
    private lateinit var btnView: AppCompatButton

    // session / header data
    private var orgCode = ""
    private var shipmentNo = ""
    private var truckNo = ""
    private var weighmentType = ""
    private var senderOrgCode = ""
    private var receiverOrgCode = ""

    // current bale
    private var loadedBale: BaleInfo? = null
    private var existingDetail: PSWDispatchDetailEntity? = null
    private var markedWeight = 0.0
    private var lastLookup = ""

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

        setContentView(R.layout.activity_pswdispatch_details)

        sessionManager = SessionManager(this)
        orgCode = sessionManager.getOrganizationCode().orEmpty()

        shipmentNo = intent.getStringExtra(PswDispatch.EXTRA_SHIPMENT_NO).orEmpty()
        truckNo = intent.getStringExtra(PswDispatch.EXTRA_TRUCK_NO).orEmpty()

        if (shipmentNo.isBlank()) {
            toast("Shipment not found")
            finish()
            return
        }

        initViews()
        setupInputs()
        setupClickListeners()
        observeViewModel()

        viewModel.loadPswHeader(shipmentNo)
        viewModel.loadPswDetails(shipmentNo)
    }

    override fun onStart() {
        super.onStart()
        if (edtBaleNo.isEnabled) edtBaleNo.requestFocus()
    }

    // -------------------------------------------------
    // VIEWS
    // -------------------------------------------------

    private fun initViews() {

        txtShipmentNo = findViewById(R.id.txt_shipmentNo)
        txtTruckNo = findViewById(R.id.txt_truck_no)
        txtLotNo = findViewById(R.id.txt_lot_no)
        txtMrkdWeight = findViewById(R.id.txt_mrkd_weight)
        txtGrade = findViewById(R.id.txt_grade)
        txtSubInvCode = findViewById(R.id.txt_sub_inv_code)
        txtRate = findViewById(R.id.txt_rate)
        txtCount = findViewById(R.id.txt_count)
        txtNote = findViewById(R.id.txt_note)

        edtBaleNo = findViewById(R.id.edt_bale_no)
        edtWeight = findViewById(R.id.edt_weight)
        chkWeight = findViewById(R.id.checkbox_weight)

        btnSave = findViewById(R.id.btnSave)
        btnClear = findViewById(R.id.btn_clear)
        btnDelete = findViewById(R.id.btn_delete)
        btnGradewise = findViewById(R.id.btn_gradewise)
        btnView = findViewById(R.id.btn_view)

        findViewById<TextView>(R.id.txt_org_code).text = orgCode
        findViewById<TextView>(R.id.txt_date).text =
            SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())

        txtShipmentNo.text = shipmentNo
        txtTruckNo.text = truckNo
        txtCount.text = "0"
        txtNote.text = ""
        txtRate.text = ""
    }

    // -------------------------------------------------
    // INPUTS
    // -------------------------------------------------

    private fun setupInputs() {

        // capital letters, max 31 characters
        edtBaleNo.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(BALE_LENGTH))

        // weight: up to 5 digits and 1 decimal
        edtWeight.filters = arrayOf(DecimalInputFilter(5, 1), InputFilter.LengthFilter(5))

        edtBaleNo.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {

                val bale = s?.toString().orEmpty()

                when {
                    bale.length == BALE_LENGTH -> onBaleEntered(bale)
                    bale.length < BALE_LENGTH -> lastLookup = ""
                }
            }
        })

        // keyboard "Done" / scanner Enter -> search the typed bale
        edtBaleNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val bale = edtBaleNo.text.toString().trim()
                if (bale.length == BALE_LENGTH) {
                    onBaleEntered(bale, forced = true)
                } else {
                    edtBaleNo.error = "Only 31 digit Case Number is accepted"
                }
                true
            } else false
        }

        // long press on the bale box -> open the camera scanner
        edtBaleNo.setOnLongClickListener {
            if (edtBaleNo.isEnabled) startBarcodeScan()
            true   // consume, so the paste/select menu does not open
        }

        // weight checkbox: shows / hides the dispatch weight row
        chkWeight.setOnCheckedChangeListener { _, checked ->
            setWeightRowVisible(checked)
            edtWeight.setText("")
        }
    }

    /** The "Dispatch Weight" label + box live in one row (the parent of edt_weight). */
    private fun setWeightRowVisible(visible: Boolean) {
        (edtWeight.parent as View).visibility = if (visible) View.VISIBLE else View.GONE
    }

    // -------------------------------------------------
    // CLICKS
    // -------------------------------------------------

    private fun setupClickListeners() {

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            onSaveClicked()
        }

        btnClear.setOnClickListener {
            clearFields()
        }

        btnDelete.setOnClickListener {
            onDeleteClicked()
        }

        btnGradewise.setOnClickListener {
            if (viewModel.details.value.isEmpty()) {
                showError("No Bales Added", "No Bales Are Added Yet")
            } else {
                startActivity(
                    Intent(this, PSWDispatchSummary::class.java)
                        .putExtra(PswDispatch.EXTRA_SHIPMENT_NO, shipmentNo)
                )
            }
        }

        btnView.setOnClickListener {
            if (viewModel.details.value.isEmpty()) {
                showError("No Bales Added", "No Bales Are Added Yet")
            } else {
                startActivity(
                    Intent(this, PSWDispatchView::class.java)
                        .putExtra(PswDispatch.EXTRA_SHIPMENT_NO, shipmentNo)
                )
            }
        }
    }

    // -------------------------------------------------
    // OBSERVE
    // -------------------------------------------------

    private fun observeViewModel() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.header.collect { state ->
                        when (state) {
                            is PswHeaderState.Loading -> {
                            }
                            is PswHeaderState.Loaded -> onHeaderLoaded(state.header)
                            is PswHeaderState.Missing -> {
                                showError(
                                    "Header Not Found",
                                    "This PSW dispatch header is not available on the device.\nPlease go back and refresh."
                                ) { finish() }
                            }
                        }
                    }
                }

                launch {
                    viewModel.baleLookup.collect { state ->
                        when (state) {
                            is PswBaleLookupState.Idle -> {
                            }
                            is PswBaleLookupState.Found -> {
                                viewModel.resetBaleLookup()
                                onBaleFound(state.info)
                            }
                            is PswBaleLookupState.NotFound -> {
                                viewModel.resetBaleLookup()
                                onBaleNotFound()
                            }
                            is PswBaleLookupState.Existing -> {
                                viewModel.resetBaleLookup()
                                onBaleAlreadyScanned(state.detail)
                            }
                            is PswBaleLookupState.InOtherShipment -> {
                                viewModel.resetBaleLookup()
                                showError("Bale Not Available", "Bale Dispatched in some other header") {
                                    clearFields()
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.details.collect { list ->

                        val pending = list.count {
                            it.syncStatus != PSWDispatchDetailEntity.SYNC_SUCCESS
                        }

                        val total = list.size.toString()

                        val text = SpannableStringBuilder(total)

                        // total count -> green
                        text.setSpan(
                            ForegroundColorSpan(COLOR_COUNT_OK),
                            0,
                            total.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        // not uploaded yet -> red
                        if (pending > 0) {
                            val start = text.length
                            text.append(" ($pending pending)")
                            text.setSpan(
                                ForegroundColorSpan(COLOR_COUNT_PENDING),
                                start,
                                text.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }

                        txtCount.text = text
                    }
                }
            }
        }
    }

    // -------------------------------------------------
    // HEADER -> weighment mode
    // -------------------------------------------------

    private fun onHeaderLoaded(header: PswDispatchHeaderEntity) {

        weighmentType = header.weighmentType.orEmpty()
        senderOrgCode = header.senderOrgnCode ?: orgCode
        receiverOrgCode = header.receiverOrgnCode.orEmpty()

        if (truckNo.isBlank()) {
            truckNo = header.senderTruckNo.orEmpty()
            txtTruckNo.text = truckNo
        }

        applyWeighmentMode()
    }

    private fun applyWeighmentMode() {

        when (weighmentType) {

            HND -> {
                // 100% : weighing is compulsory
                chkWeight.isChecked = true
                chkWeight.isEnabled = false
                setWeightRowVisible(true)
                txtNote.text = "Note: You Select 100% Weighment"
            }

            TWT -> {
                // 20% : weighing is optional
                chkWeight.isEnabled = true
                chkWeight.isChecked = false
                setWeightRowVisible(false)
                txtNote.text = "Note: You Select 20% Weighment"
            }

            else -> {
                txtNote.text = ""
            }
        }
    }

    // -------------------------------------------------
    // BALE ENTRY
    // -------------------------------------------------

    private fun onBaleEntered(bale: String, forced: Boolean = false) {

        if (bale == lastLookup && !forced) return

        lastLookup = bale

        edtBaleNo.error = null

        // this shipment -> other shipment -> stock table (answer comes in observeViewModel)
        viewModel.lookupPswBale(bale, shipmentNo)
    }

    private fun startBarcodeScan() {

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_QR_CODE
            )
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode ->

                // keep only A-Z / 0-9, capitals, max 31 (same rules as the box)
                val baleNo = barcode.rawValue.orEmpty()
                    .uppercase(Locale.US)
                    .filter { it.isLetterOrDigit() }
                    .take(BALE_LENGTH)

                if (baleNo.isBlank()) {
                    toast("Barcode is empty")
                } else {
                    openScannedBale(baleNo)
                }
            }
            .addOnCanceledListener { /* user closed the scanner */ }
            .addOnFailureListener { e ->
                toast("Scanner not available: ${e.message}")
            }
    }

    /** Clears the screen, sets the text; the TextWatcher (31 chars) starts the lookup. */
    private fun openScannedBale(baleNo: String) {
        clearFields()
        edtBaleNo.setText(baleNo)
        edtBaleNo.setSelection(edtBaleNo.text.length)
    }

    /** bale is already in this shipment */
    private fun onBaleAlreadyScanned(existing: PSWDispatchDetailEntity) {

        AppAlertDialog(
            activity = this,
            title = "Bale Already Scanned",
            message = "Bale is already scanned for dispatch.\nDo you want to update it?",
            type = AppAlertDialog.Type.WARNING,
            positiveText = "Update",
            negativeText = "Cancel",
            onNegative = { clearFields() }
        ) {
            enterUpdateMode(existing)
        }.show()
    }

    /** old PSW: dialog, then the box is cleared */
    private fun onBaleNotFound() {
        showError("Bale Not Available", "Bale Not Available In The Stock") {
            clearFields()
        }
    }

    private fun onBaleFound(info: BaleInfo) {

        // bale must be available in this organization (old: status Y, process N, same org)
        val available =
            info.status == "Y" &&
                    info.processStatus == "N" &&
                    info.currentOrgnCode == orgCode

        if (!available) {
            showError("Bale Not Available", "Bale in some other process") {
                clearFields()
            }
            return
        }

        val marked = info.markedWeight.toDoubleOrNull()

        if (marked == null) {
            showError("Weight Missing", "Marked weight is not available for this bale") {
                clearFields()
            }
            return
        }

        loadedBale = info
        existingDetail = null
        markedWeight = marked

        val grade = info.grade.ifBlank { info.buyerGrade }

        txtLotNo.text = info.tbLotNumber
        txtMrkdWeight.text = info.markedWeight
        txtGrade.text = grade
        txtSubInvCode.text = info.subInventoryCode
        txtRate.text = rateText(info.price, grade)

        btnSave.text = "Save"
        edtBaleNo.isEnabled = false

        if ((edtWeight.parent as View).visibility == View.VISIBLE) {
            edtWeight.setText("")
            edtWeight.requestFocus()
            showKeyboard(edtWeight)
        }
    }

    private fun rateText(price: String, grade: String): String =
        if (price.isBlank() || price == "0") "Rate for this $grade not exist" else price

    private fun enterUpdateMode(existing: PSWDispatchDetailEntity) {

        existingDetail = existing
        loadedBale = null
        markedWeight = existing.markedWt

        txtLotNo.text = existing.tbLotNumber.orEmpty()
        txtMrkdWeight.text = formatWeight(existing.markedWt)
        txtGrade.text = existing.grade.orEmpty()
        txtSubInvCode.text = existing.fromSubinventoryCode.orEmpty()
        txtRate.text = rateText(existing.price.orEmpty(), existing.grade.orEmpty())

        // checkbox first (its listener clears the weight), then the weight itself
        if (weighmentType == TWT) {
            chkWeight.isChecked = existing.dispatchWeighmentStatus == "Y"
        }

        val weighing = weighmentType == HND || chkWeight.isChecked

        if (weighing && existing.dispatchWeight > 0.0) {
            edtWeight.setText(formatWeight(existing.dispatchWeight))
        }

        btnSave.text = "Update"
        edtBaleNo.isEnabled = false

        if (weighing) {
            edtWeight.requestFocus()
            showKeyboard(edtWeight)
        }
    }

    // -------------------------------------------------
    // SAVE  (old PSW checks)
    // -------------------------------------------------

    private fun normalizeWeight(raw: String): String = when {
        Regex("^\\.\\d+$").matches(raw) -> "0$raw"
        Regex("^[-+]?\\d+\\.$").matches(raw) -> raw + "0"
        else -> raw
    }

    private fun onSaveClicked() {

        val bale = edtBaleNo.text.toString().trim()

        if (bale.isEmpty()) {
            toast("Please Enter The Bale")
            edtBaleNo.error = "Enter The Bale Number"
            edtBaleNo.requestFocus()
            return
        }

        if (bale.length != BALE_LENGTH) {
            edtBaleNo.error = "Only 31 digit Case Number is accepted"
            edtBaleNo.requestFocus()
            return
        }

        if (weighmentType != TWT && weighmentType != HND) {
            showError("Weighment Type", "Please Select The Weighment Type")
            return
        }

        if (txtMrkdWeight.text.isNullOrBlank() || (loadedBale == null && existingDetail == null)) {
            toast("Data is Loading")
            return
        }

        val weighing = weighmentType == HND || chkWeight.isChecked

        var weightText = ""

        if (weighing) {

            val typed = edtWeight.text.toString().trim()
            weightText = normalizeWeight(typed)

            if (weightText != typed) edtWeight.setText(weightText)

            if (weightText.isEmpty()) {
                weightError("0 Value Not Allowed In Weight", clear = false)
                return
            }

            val weight = weightText.toDoubleOrNull()

            if (weight == null) {
                showError("Invalid Weight", "Entered values are not valid")
                weightError("Enter a Valid Dispatch Weight", clear = false)
                return
            }

            if (weight <= 0.0) {
                weightError("0 Value Not Allowed In Weight", clear = true)
                return
            }

            if (weight > MAX_WEIGHT) {
                weightError("Weight Allows Only Below ${MAX_WEIGHT.toInt()}", clear = true)
                return
            }

            // old PSW: no "greater than marked weight" check

            // weight loss more than 1.5 % (same formula as the old app)
            val weightLoss = (weight * WEIGHT_LOSS_PERCENT) / 100.0
            val lowLimit = markedWeight - weightLoss

            if (weight < lowLimit) {

                if (sessionManager.getUserRights() == "A") {
                    confirmWeightLoss { saveDetail(bale, true, weightText) }
                } else {
                    showError(
                        "Weight Loss",
                        "Weight loss is more than 1.5%, You don't have the right to accept.\nPlease inform the supervisor/buyer."
                    )
                }
                return
            }
        }

        saveDetail(bale, weighing, weightText)
    }

    private fun weightError(message: String, clear: Boolean) {
        edtWeight.error = message
        edtWeight.requestFocus()
        if (clear) edtWeight.setText("")
    }

    private fun confirmWeightLoss(onAccept: () -> Unit) {
        AppAlertDialog(
            activity = this,
            title = "Weight Loss",
            message = "Weight loss is more than 1.5%.\nDo you want to accept the weight loss?",
            type = AppAlertDialog.Type.WARNING,
            positiveText = "Accept",
            negativeText = "Cancel"
        ) {
            onAccept()
        }.show()
    }

    /** Builds the detail record and stores it on the device (PENDING). */
    private fun saveDetail(bale: String, weighing: Boolean, weightText: String) {

        val base = existingDetail
        val info = loadedBale

        val fromSub = txtSubInvCode.text.toString()

        val marked = txtMrkdWeight.text.toString().toDoubleOrNull() ?: 0.0

        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

        val detail = PSWDispatchDetailEntity(
            shipmentNo = shipmentNo,
            detailId = base?.detailId
                ?: (Build.MODEL + "GSH" + orgCode + TimeUtils.shipmentDatePart()),
            baleNumber = bale,
            grade = txtGrade.text.toString(),
            markedWt = marked,
            // not weighed -> dispatch weight = marked weight (old rule)
            dispatchWeight = if (weighing) weightText.toDouble() else marked,
            fromSubinventoryCode = fromSub,
            toSubinventoryCode = fromSub,                 // old PSW: same as from
            createdBy = sessionManager.getUserId().orEmpty(),
            createdDate = base?.createdDate ?: TimeUtils.senderDate(),
            senderOrgnCode = senderOrgCode,
            receiverOrgnCode = receiverOrgCode,
            dispatchWeighmentStatus = if (chkWeight.isChecked) "Y" else "N",
            processType = null,                           // set at upload time, like Dispatch
            deviceId = deviceId,
            price = info?.price?.ifBlank { "0" } ?: base?.price ?: "0",
            tbLotNumber = txtLotNo.text.toString(),
            purchaseDate = info?.purchaseDate ?: base?.purchaseDate.orEmpty()
        )

        viewModel.savePswDetail(detail) { wasUpdate ->
            toast(if (wasUpdate) "Bale updated" else "Bale saved")
            clearFields()
        }
    }

    // -------------------------------------------------
    // DELETE
    // -------------------------------------------------

    private fun onDeleteClicked() {

        val bale = edtBaleNo.text.toString().trim()

        if (bale.isEmpty() || txtGrade.text.isNullOrBlank()) {
            edtBaleNo.error = "Enter the valid Bale Number"
            return
        }

        val existing = viewModel.findPswDetail(bale)

        if (existing == null) {
            showError("Bale Not Available", "Bale Not Available")
            return
        }

        // already on the server: there is no delete API yet
        if (existing.syncStatus == PSWDispatchDetailEntity.SYNC_SUCCESS) {
            showError(
                "Already Uploaded",
                "This bale is already uploaded to the server.\nDelete is not available yet."
            )
            return
        }

        DeleteConfirmDialog(
            activity = this,
            title = "Delete Bale?",
            highlight = bale,
            message = "This bale will be removed from this shipment.\nThis action cannot be undone."
        ) {
            viewModel.deletePswDetail(existing) {
                toast("Deleted Successfully")
                clearFields()
            }
        }.show()
    }

    // -------------------------------------------------
    // CLEAR
    // -------------------------------------------------

    private fun clearFields() {

        edtBaleNo.isEnabled = true
        edtBaleNo.setText("")
        edtBaleNo.error = null

        txtLotNo.text = ""
        txtMrkdWeight.text = ""
        txtGrade.text = ""
        txtSubInvCode.text = ""
        txtRate.text = ""

        edtWeight.setText("")
        edtWeight.error = null
        edtWeight.clearFocus()

        loadedBale = null
        existingDetail = null
        markedWeight = 0.0
        lastLookup = ""

        btnSave.text = "Save"

        edtBaleNo.requestFocus()
    }

    // -------------------------------------------------
    // HELPERS
    // -------------------------------------------------

    private fun formatWeight(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(title: String, message: String, onOk: () -> Unit = {}) {
        AppAlertDialog(
            activity = this,
            title = title,
            message = message
        ) {
            onOk()
        }.show()
    }

    private fun showKeyboard(view: EditText) {
        view.postDelayed({
            view.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }
}