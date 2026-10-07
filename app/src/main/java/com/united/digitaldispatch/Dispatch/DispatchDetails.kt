package com.united.digitaldispatch.Dispatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
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
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.BaleLookupState
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.DispatchViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.HeaderState
import com.united.digitaldispatch.Dispatch.models.BaleDetail
import com.united.digitaldispatch.Dispatch.models.BaleInfo
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.DispatchHeaderEntity
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
 * Dispatch Details (scan bales of one shipment).
 *
 * BASE VERSION:
 *  - all validations of the old DispatchDetailsActivity are in
 *  - the weighment type (20% / 100%) comes from the header:
 *        TWT (20%)  -> weight checkbox optional
 *        HND (100%) -> weight checkbox compulsory (ticked + locked)
 *  - scanned bales are kept in memory only (DispatchViewModel);
 *    saving to the server / local DB is added when the details API is ready
 */
class DispatchDetails : AppCompatActivity() {

    private companion object {
        const val TWT = "TWT"
        const val HND = "HND"

        const val MAX_HND_WEIGHT = 200f
        const val WEIGHT_LOSS_PERCENT = 1.5f
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
    private var moduleType = ""
    private var shipmentNo = ""
    private var truckNo = ""
    private var weighmentType = ""
    private var senderOrgCode = ""
    private var receiverOrgCode = ""

    // current bale
    private var loadedBale: BaleInfo? = null
    private var existingDetail: BaleDetail? = null
    private var markedWeight = 0f
    private var lastLookup = ""

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

        setContentView(R.layout.activity_dispatch_details)

        sessionManager = SessionManager(this)
        orgCode = sessionManager.getOrganizationCode().orEmpty()
        moduleType = sessionManager.getModuleType().orEmpty()

        shipmentNo = intent.getStringExtra(Dispatch.EXTRA_SHIPMENT_NO).orEmpty()
        truckNo = intent.getStringExtra(Dispatch.EXTRA_TRUCK_NO).orEmpty()

        if (shipmentNo.isBlank()) {
            toast("Shipment not found")
            finish()
            return
        }

        initViews()
        setupInputs()
        setupClickListeners()
        observeViewModel()

        viewModel.loadHeader(shipmentNo)
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

        // capital letters, max 14 characters
        edtBaleNo.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(14))

        // weight: up to 5 digits and 1 decimal (old DecimalDigitsInputFilter(5,1))
        edtWeight.filters = arrayOf(DecimalInputFilter(5, 1), InputFilter.LengthFilter(5))

        edtBaleNo.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {

                val bale = s?.toString().orEmpty()

                when {
                    bale.length == 13 || bale.length == 14 -> onBaleEntered(bale)
                    bale.length < 13 -> lastLookup = ""
                }
            }
        })

        // keyboard "Done" / scanner Enter -> search the typed bale
        edtBaleNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val bale = edtBaleNo.text.toString().trim()
                if (bale.length == 13 || bale.length == 14) {
                    onBaleEntered(bale, forced = true)
                } else {
                    edtBaleNo.error = "Please Enter Valid Bale Number"
                }
                true
            } else false
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
                // TODO: data upload (offline-first sync) before the summary, once the API is ready
                startActivity(
                    Intent(this, DispatchSummary::class.java)
                        .putExtra(Dispatch.EXTRA_SHIPMENT_NO, shipmentNo)
                )
            }
        }

        btnView.setOnClickListener {
            if (viewModel.details.value.isEmpty()) {
                showError("No Bales Added", "No Bales Are Added Yet")
            } else {
                startActivity(
                    Intent(this, DispatchView::class.java)
                        .putExtra(Dispatch.EXTRA_SHIPMENT_NO, shipmentNo)
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
                            is HeaderState.Loading -> {
                            }
                            is HeaderState.Loaded -> onHeaderLoaded(state.header)
                            is HeaderState.Missing -> {
                                showError(
                                    "Header Not Found",
                                    "This dispatch header is not available on the device.\nPlease go back and refresh."
                                ) { finish() }
                            }
                        }
                    }
                }

                launch {
                    viewModel.baleLookup.collect { state ->
                        when (state) {
                            is BaleLookupState.Idle -> {
                            }
                            is BaleLookupState.Found -> {
                                viewModel.resetBaleLookup()
                                onBaleFound(state.info)
                            }
                            is BaleLookupState.NotFound -> {
                                viewModel.resetBaleLookup()
                                onBaleNotFound(state.baleNo)
                            }
                        }
                    }
                }

                launch {
                    viewModel.details.collect { list ->
                        txtCount.text = list.size.toString()
                    }
                }
            }
        }
    }

    // -------------------------------------------------
    // HEADER -> weighment mode
    // -------------------------------------------------

    private fun onHeaderLoaded(header: DispatchHeaderEntity) {

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

    /**
     * Old rules:
     *  - digits at position 7..12  -> valid
     *  - 14 chars: GLT, or WH with 'P' at position 8
     *  - 13 chars: GLT / WH / PPD with 'P' at position 7
     */
    private fun isValidBaleFormat(bale: String): Boolean {

        if (bale.length < 13) return false

        val middle = bale.substring(7, 13)

        if (middle.all { it in '0'..'9' }) return true

        return when (bale.length) {
            14 -> moduleType == "GLT" || (moduleType == "WH" && bale[8] == 'P')
            13 -> (moduleType == "GLT" || moduleType == "WH" || moduleType == "PPD") && bale[7] == 'P'
            else -> false
        }
    }

    private fun onBaleEntered(bale: String, forced: Boolean = false) {

        if (bale == lastLookup && !forced) return

        lastLookup = bale

        if (!isValidBaleFormat(bale)) {
            showError("Invalid Bale Number", "Please check the bale number and try again") {
                clearFields()
            }
            return
        }

        edtBaleNo.error = null

        // already scanned in this shipment -> offer to update it
        val existing = viewModel.findDetail(bale)

        if (existing != null) {
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
            return
        }

        viewModel.lookupBale(bale)
    }

    private fun onBaleNotFound(bale: String) {

        edtBaleNo.error = "No Bale Number Found"

        // a 14 character bale is still being typed when the 13th character arrives,
        // so only clear the box when the full length (or Done) was reached
        if (bale.length >= 14) {
            edtBaleNo.setText("")
        }

        edtBaleNo.requestFocus()
    }

    private fun onBaleFound(info: BaleInfo) {

        // not TAP -> the bale must be classified
        if (moduleType != "TAP" && info.grade.isBlank()) {
            showError("Classification Pending", "Please Complete Classification For this Bale") {
                clearFields()
            }
            return
        }

        // bale must be available in this organization
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

        val marked = info.markedWeight.toFloatOrNull()

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

    private fun enterUpdateMode(existing: BaleDetail) {

        existingDetail = existing
        loadedBale = null
        markedWeight = existing.markedWeight.toFloatOrNull() ?: 0f

        txtLotNo.text = existing.tbLotNumber
        txtMrkdWeight.text = existing.markedWeight
        txtGrade.text = existing.grade
        txtSubInvCode.text = existing.fromSubinventoryCode
        txtRate.text = rateText(existing.price, existing.grade)

        // checkbox first (its listener clears the weight), then the weight itself
        if (weighmentType == TWT) {
            chkWeight.isChecked = existing.dispatchWeighmentStatus == "Y"
        }

        val weighing = weighmentType == HND || chkWeight.isChecked

        if (weighing && existing.dispatchWeight != "0" && existing.dispatchWeight.isNotBlank()) {
            edtWeight.setText(existing.dispatchWeight)
        }

        btnSave.text = "Update"
        edtBaleNo.isEnabled = false

        if (weighing) {
            edtWeight.requestFocus()
            showKeyboard(edtWeight)
        }
    }

    // -------------------------------------------------
    // SAVE  (all old checks)
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

        if (bale.length < 13) {
            edtBaleNo.error = "Please Enter Valid Bale Number"
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

        var weight = 0f
        var weightText = ""

        if (weighing) {

            val typed = edtWeight.text.toString().trim()
            weightText = normalizeWeight(typed)

            if (weightText != typed) edtWeight.setText(weightText)

            if (weightText.isEmpty()) {
                weightError("0 Value Not Allowed In Weight", clear = false)
                return
            }

            val parsed = weightText.toFloatOrNull()

            if (parsed == null) {
                showError("Invalid Weight", "Entered values are not valid")
                weightError("Enter a Valid Dispatch Weight", clear = false)
                return
            }

            if (parsed <= 0f) {
                weightError("0 Value Not Allowed In Weight", clear = true)
                return
            }

            if (weighmentType == HND && parsed > MAX_HND_WEIGHT) {
                weightError("Weight Allows Only Below ${MAX_HND_WEIGHT.toInt()}", clear = true)
                return
            }

            if (parsed > markedWeight) {
                weightError("Dispatch Weight cannot be Greater Than Markt Weight", clear = true)
                return
            }

            weight = parsed

            // weight loss more than 1.5 %
            val weightLoss = (weight * WEIGHT_LOSS_PERCENT) / 100f
            val lowLimit = markedWeight - weightLoss

            if (weight < lowLimit) {

                if (weighmentType == HND) {
                    confirmWeightLoss { saveDetail(bale, true, weightText) }
                } else {
                    // 20% : only a supervisor (rights "A") may accept a weight loss
                    if (sessionManager.getUserRights() == "A") {
                        confirmWeightLoss { saveDetail(bale, true, weightText) }
                    } else {
                        showError(
                            "Weight Loss",
                            "Weight loss is more than 1.5%, You don't have the right to accept.\nPlease inform the supervisor/buyer."
                        )
                    }
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

    /**
     * Builds the detail record and stores it.
     *
     * TODO (offline-first): when the details API is ready
     *   - network available -> send to the server
     *   - otherwise         -> save in the local table and sync later
     * For now it is kept in the view model only.
     */
    private fun saveDetail(bale: String, weighing: Boolean, weightText: String) {

        val base = existingDetail
        val info = loadedBale

        val fromSub = txtSubInvCode.text.toString()

        val detail = BaleDetail(
            shipmentNo = shipmentNo,
            detailId = base?.detailId
                ?: (Build.MODEL + "GSH" + orgCode + TimeUtils.shipmentDatePart()),
            gpilBaleNumber = bale,
            markedWeight = txtMrkdWeight.text.toString(),
            // 20% and no weighing -> dispatch weight = marked weight (old rule)
            dispatchWeight = if (weighing) weightText else txtMrkdWeight.text.toString(),
            fromSubinventoryCode = fromSub,
            toSubinventoryCode = if (moduleType == "TAP") "CL" else fromSub,
            grade = txtGrade.text.toString(),
            createdBy = sessionManager.getUserId().orEmpty(),
            createdDate = base?.createdDate ?: TimeUtils.senderDate(),
            senderOrgnCode = senderOrgCode,
            receiverOrgnCode = receiverOrgCode,
            price = info?.price?.ifBlank { "0" } ?: base?.price ?: "0",
            tbLotNumber = txtLotNo.text.toString(),
            purchaseDate = info?.purchaseDate ?: base?.purchaseDate.orEmpty(),
            dispatchWeighmentStatus = if (chkWeight.isChecked) "Y" else "N"
        )

        val wasUpdate = base != null

        viewModel.saveDetail(detail)

        toast(if (wasUpdate) "Bale updated successfully" else "Bale saved successfully")

        clearFields()
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

        if (viewModel.findDetail(bale) == null) {
            showError("Bale Not Available", "Bale Not Available")
            return
        }

        DeleteConfirmDialog(
            activity = this,
            title = "Delete Bale?",
            highlight = bale,
            message = "This bale will be removed from this shipment.\nThis action cannot be undone."
        ) {
            // TODO: delete on the server / local table too, once the API is ready
            viewModel.deleteDetail(bale)
            toast("Deleted Successfully")
            clearFields()
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
        markedWeight = 0f
        lastLookup = ""

        btnSave.text = "Save"

        edtBaleNo.requestFocus()
    }

    // -------------------------------------------------
    // HELPERS
    // -------------------------------------------------

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