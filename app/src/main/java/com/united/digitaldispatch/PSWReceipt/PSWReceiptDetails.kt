package com.united.digitaldispatch.PSWReceipt

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.PswReceiptRepository
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptDetailEvent
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptSyncState
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.PswReceiptDetailEntity
import com.united.digitaldispatch.sync.NetworkMonitor
import com.united.digitaldispatch.sync.SyncManager
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PSWReceiptDetails : AppCompatActivity() {

    companion object {
        const val EXTRA_SHIPMENT_NO = "extra_psw_receipt_shipment_no"

        private const val WEIGH_20 = "TWT"
        private const val WEIGH_100 = "HND"
        private const val SCAN_DEBOUNCE_MS = 300L

        // old PSW rules
        private const val CASE_NO_LENGTH = 31
        private const val MAX_WEIGHT = 300.0

        // old screen: weight is prefilled with the marked weight and the box is locked.
        // set to false if PSW users should type the weight.
        private const val WEIGHT_LOCKED_TO_MARKED = true
    }

    private val COLOR_COUNT_OK = Color.parseColor("#2E7D32")
    private val COLOR_COUNT_PENDING = Color.parseColor("#C0392B")

    private lateinit var sessionManager: SessionManager
    private lateinit var shipmentNo: String

    private lateinit var rbTwenty: RadioButton
    private lateinit var rbHundred: RadioButton
    private lateinit var chkWeight: CheckBox

    private lateinit var edtBale: EditText      // = Case Number
    private lateinit var edtWeight: EditText

    private lateinit var txtGrade: TextView
    private lateinit var txtSubCode: TextView
    private lateinit var txtMarked: TextView
    private lateinit var txtReceived: TextView
    private lateinit var txtWeighted: TextView

    private lateinit var llWeight: View
    private lateinit var llWeighted: View

    private lateinit var btnSave: AppCompatButton
    private lateinit var btnClear: AppCompatButton
    private lateinit var btnSummary: AppCompatButton
    private lateinit var btnView: AppCompatButton
    private lateinit var btnBack: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null

    private var lookupInFlight = false
    private var balesLoading = false

    private var currentBale: PswReceiptDetailEntity? = null

    private var markedWt = 0.0
    private var currWt = 0.0
    private var weighType = ""

    private var totalBales = 0
    private var receivedBales = 0
    private var weighedBales = 0
    private var pendingBales = 0

    private val viewModel: PswReceiptViewModel by viewModels {
        PswReceiptViewModelFactory(
            PswReceiptRepository(
                ApiClient.instance,
                AppDatabase.getInstance(applicationContext).pswReceiptHeaderDao(),
                AppDatabase.getInstance(applicationContext).pswReceiptDetailDao()
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswreceipt_details)

        sessionManager = SessionManager(this)

        shipmentNo = intent.getStringExtra(EXTRA_SHIPMENT_NO).orEmpty()

        if (shipmentNo.isBlank()) {
            Toast.makeText(this, "PSW Shipment missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel.setShipment(shipmentNo)

        initViews()
        setupInputFilters()
        setupListeners()
        setupBaleInput()
        observe()

        viewModel.resolveSavedWeighType()
        viewModel.downloadBales(shipmentNo)
    }

    override fun onDestroy() {
        scanRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun initViews() {

        findViewById<TextView>(R.id.txt_org_code).text =
            sessionManager.getOrganizationCode().orEmpty()

        findViewById<TextView>(R.id.txt_date).text =
            SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())

        findViewById<TextView>(R.id.txt_shipmentNo).text = shipmentNo

        val org = sessionManager.getModuleType().orEmpty()
        findViewById<TextView>(R.id.txt_title).text =
            if (org.isNotBlank()) "PSW Receipt ($org)" else "PSW Receipt"

        rbTwenty = findViewById(R.id.rb_twentyPercent)
        rbHundred = findViewById(R.id.rb_hundredPercent)

        chkWeight = findViewById(R.id.chk_weight)

        edtBale = findViewById(R.id.edt_baleNumber)
        edtWeight = findViewById(R.id.ed_weight)

        txtGrade = findViewById(R.id.txt_grade)
        txtSubCode = findViewById(R.id.sub_code)

        txtMarked = findViewById(R.id.txt_markedWeight)
        txtReceived = findViewById(R.id.txt_receivedBales)
        txtWeighted = findViewById(R.id.txt_weightedBales)

        llWeight = findViewById(R.id.ll_weightContainer)
        llWeighted = findViewById(R.id.ll_weightedBales)

        btnSave = findViewById(R.id.btn_save)
        btnClear = findViewById(R.id.btn_clear)
        btnSummary = findViewById(R.id.btn_summary)
        btnView = findViewById(R.id.btn_view)
        btnBack = findViewById(R.id.btn_back)

        // old screen: checkbox hidden until 20% is chosen
        chkWeight.visibility = View.GONE
        llWeight.visibility = View.GONE
        llWeighted.visibility = View.GONE
        btnSave.visibility = View.GONE

        if (WEIGHT_LOCKED_TO_MARKED) edtWeight.isEnabled = false

        edtBale.requestFocus()
    }

    private fun setupInputFilters() {

        // case number: capitals + digits only, max 31
        val alnum = InputFilter { source, start, end, _, _, _ ->
            val kept = source.subSequence(start, end).filter { it.isLetterOrDigit() }
            if (kept.length == end - start) null else kept
        }
        edtBale.filters = arrayOf(
            InputFilter.AllCaps(),
            alnum,
            InputFilter.LengthFilter(CASE_NO_LENGTH)
        )

        // weight: up to 3 digits + 1 decimal. When locked, the box only shows the marked weight.
        if (WEIGHT_LOCKED_TO_MARKED) {
            edtWeight.filters = emptyArray()
        } else {
            val decimalFilter = InputFilter { source, start, end, dest, dstart, dend ->
                val result = dest.substring(0, dstart) +
                        source.subSequence(start, end) +
                        dest.substring(dend)
                if (Regex("^\\d{0,3}(\\.\\d?)?$").matches(result)) null else ""
            }
            edtWeight.filters = arrayOf(InputFilter.LengthFilter(5), decimalFilter)
        }
    }

    private fun setupListeners() {

        rbTwenty.setOnClickListener {
            if (rbTwenty.isChecked) onWeighTypeSelected(WEIGH_20)
        }

        rbHundred.setOnClickListener {
            if (rbHundred.isChecked) onWeighTypeSelected(WEIGH_100)
        }

        chkWeight.setOnCheckedChangeListener { _, checked ->

            val visibility = if (checked) View.VISIBLE else View.GONE

            llWeight.visibility = visibility
            btnSave.visibility = visibility

            if (!checked) {
                edtWeight.setText("")
                currWt = 0.0
            }
        }

        btnSave.setOnClickListener { onSaveClicked() }

        btnClear.setOnClickListener { clearFields() }

        btnBack.setOnClickListener { finish() }

        // old screen: long press on the case number box opens the camera scanner
        edtBale.setOnLongClickListener {
            if (edtBale.isEnabled) startBarcodeScan()
            true
        }

        btnView.setOnClickListener {
           /* startActivity(
                Intent(this, PSWReceiptView::class.java)
                    .putExtra(PSWReceiptView.EXTRA_SHIPMENT_NO, shipmentNo)
            )*/
        }

        btnSummary.setOnClickListener {
            if (receivedBales == 0) {
                showError("No Receipt Bales Are Added")
            } else {
                // old screen uploaded everything before opening the summary
                SyncManager.requestSync(applicationContext)
                startActivity(
                    Intent(this, PSWReceiptSummary::class.java)
                        .putExtra(EXTRA_SHIPMENT_NO, shipmentNo)
                )
            }
        }
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

                val caseNo = barcode.rawValue.orEmpty()
                    .uppercase(Locale.US)
                    .filter { it.isLetterOrDigit() }
                    .take(CASE_NO_LENGTH)

                when {
                    caseNo.isBlank() -> Toast.makeText(this, "Barcode is empty", Toast.LENGTH_SHORT).show()
                    caseNo.length != CASE_NO_LENGTH ->
                        Toast.makeText(this, "Invalid Case Number", Toast.LENGTH_SHORT).show()
                    else -> {
                        clearFields()
                        edtBale.setText(caseNo)   // the TextWatcher starts the lookup
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Scanner not available: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBaleInput() {

        edtBale.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {

                scanRunnable?.let { handler.removeCallbacks(it) }

                val value = s?.toString().orEmpty().trim()

                // old rule: the lookup runs when the case number reaches 31 characters
                if (value.length == CASE_NO_LENGTH) {
                    val runnable = Runnable { onBaleEntered(value) }
                    scanRunnable = runnable
                    handler.postDelayed(runnable, SCAN_DEBOUNCE_MS)
                }
            }
        })

        edtBale.setOnEditorActionListener { _, actionId, event ->

            val isEnter =
                actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_NEXT ||
                        event?.keyCode == KeyEvent.KEYCODE_ENTER

            if (isEnter) {
                scanRunnable?.let { handler.removeCallbacks(it) }

                val value = edtBale.text.toString().trim()

                if (value.isNotEmpty()) {
                    if (value.length == CASE_NO_LENGTH) onBaleEntered(value)
                    else edtBale.error = "Invalid Case Number"
                }

                true
            } else {
                false
            }
        }
    }

    private fun observe() {

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch { viewModel.totalBales.collect { totalBales = it; refreshCounts() } }
                launch { viewModel.receivedBales.collect { receivedBales = it; refreshCounts() } }
                launch { viewModel.weighedBales.collect { weighedBales = it; refreshCounts() } }
                launch { viewModel.pendingBales.collect { pendingBales = it; refreshCounts() } }
                launch { viewModel.events.collect { handleEvent(it) } }

                launch {
                    viewModel.baleSyncState.collect { state ->

                        when (state) {

                            is PswReceiptSyncState.Loading -> balesLoading = true

                            is PswReceiptSyncState.Success -> {
                                balesLoading = false
                                viewModel.resolveSavedWeighType()
                            }

                            is PswReceiptSyncState.Error -> {
                                balesLoading = false

                                if (totalBales == 0) {
                                    showError("Unable to load PSW bales: ${state.message}")
                                } else {
                                    Toast.makeText(
                                        this@PSWReceiptDetails,
                                        "Showing saved PSW bales (offline)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun refreshCounts() {

        val base = "$receivedBales / $totalBales"
        val text = SpannableStringBuilder(base)

        text.setSpan(
            ForegroundColorSpan(COLOR_COUNT_OK),
            0, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        if (pendingBales > 0) {
            val start = text.length
            text.append(" ($pendingBales pending)")
            text.setSpan(
                ForegroundColorSpan(COLOR_COUNT_PENDING),
                start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        txtReceived.text = text
        txtWeighted.text = "$weighedBales / ${weightTarget(totalBales)}"
    }

    // 20 % of the bales (min 1 when 5 bales or fewer) -- same as the old screen
    private fun weightTarget(total: Int): Int =
        if (total > 5) Math.round(total / 100.0 * 20).toInt() else 1

    private fun handleEvent(event: PswReceiptDetailEvent) {

        when (event) {

            is PswReceiptDetailEvent.BaleNotFound -> {
                lookupInFlight = false
                edtBale.error = "No Bale Number Found"
                edtBale.setText("")
                edtBale.requestFocus()
                btnSave.text = "Save"
            }

            is PswReceiptDetailEvent.BaleFound -> {
                lookupInFlight = false
                onBaleFound(event.bale)
            }

            is PswReceiptDetailEvent.Saved -> onSaved(event.isFirstBale)

            is PswReceiptDetailEvent.InitialWeighType -> applyWeighType(event.type, true)

            is PswReceiptDetailEvent.WeighTypeResolved -> {
                applyWeighType(event.type, true)

                if (event.changed) {
                    val label = if (event.type == WEIGH_20) "20% Weighment" else "100% Weighment"
                    showDialog("This shipment is already set to $label. Weighment type changed.")
                }
            }

            is PswReceiptDetailEvent.Error -> {
                lookupInFlight = false
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onWeighTypeSelected(type: String) {

        weighType = type

        if (type == WEIGH_20) {
            chkWeight.visibility = View.VISIBLE      // old: shown only for 20 %
            chkWeight.isChecked = false
            chkWeight.isEnabled = true
            llWeighted.visibility = View.VISIBLE
        } else {
            chkWeight.visibility = View.GONE         // old: hidden for 100 %
            chkWeight.isChecked = true
            chkWeight.isEnabled = false
            llWeighted.visibility = View.GONE
        }
    }

    private fun applyWeighType(type: String, lock: Boolean) {

        when (type) {
            WEIGH_20 -> rbTwenty.isChecked = true
            WEIGH_100 -> rbHundred.isChecked = true
            else -> return
        }

        onWeighTypeSelected(type)

        if (lock) lockWeighType()
    }

    private fun lockWeighType() {
        rbTwenty.isEnabled = false
        rbHundred.isEnabled = false
    }

    // ------------------------------------------------------------------
    // CASE NUMBER SCAN + VALIDATION
    // ------------------------------------------------------------------

    private fun onBaleEntered(caseNo: String) {

        if (balesLoading) {
            Toast.makeText(this, "Loading PSW bales, please wait", Toast.LENGTH_SHORT).show()
            edtBale.setText("")
            return
        }

        if (lookupInFlight || !edtBale.isEnabled) return

        if (!isValidCaseNo(caseNo)) {
            showError("Invalid Case Number")
            clearFields()
            return
        }

        if (!rbTwenty.isChecked && !rbHundred.isChecked) {
            showError("Please Select Any Weighment Type")
            clearFields()
            return
        }

        lookupInFlight = true
        viewModel.lookupBale(caseNo)
    }

    // old PSW rule: exactly 31 characters, capital letters and digits
    private fun isValidCaseNo(c: String): Boolean =
        c.length == CASE_NO_LENGTH && c.all { it.isDigit() || it in 'A'..'Z' }

    private fun onBaleFound(bale: PswReceiptDetailEntity) {

        // old behaviour: no update flow, just tell the user and stop
        if (bale.status == PswReceiptDetailEntity.STATUS_RECEIVED) {
            showDialog("This Case number already scanned") { clearFields() }
            return
        }

        fillFromBale(bale)

        // 20 % mode and bale not weighed -> save straight away (unless it MUST be weighed)
        if (rbTwenty.isChecked && !chkWeight.isChecked) {
            if (mustWeighThisBale()) {
                openForcedWeighDialog()
            } else {
                currWt = markedWt
                saveBale()
            }
        }
    }

    private fun fillFromBale(bale: PswReceiptDetailEntity) {

        currentBale = bale
        markedWt = bale.markedWt

        txtMarked.text = fmt(bale.markedWt)
        txtGrade.text = bale.grade.orEmpty()
        // old screen showed the TO sub-inventory code
        txtSubCode.text = bale.toSubinventoryCode ?: bale.fromSubinventoryCode.orEmpty()

        // old screen: weight is prefilled with the marked weight and locked
        if (WEIGHT_LOCKED_TO_MARKED) {
            edtWeight.setText(fmt(bale.markedWt))
            edtWeight.isEnabled = false
        }

        edtBale.isEnabled = false
        if (!WEIGHT_LOCKED_TO_MARKED) edtWeight.requestFocus()
    }

    // remaining bales can only just reach the 20 % target -> this one has to be weighed
    private fun mustWeighThisBale(): Boolean {

        if (!(rbTwenty.isChecked && llWeighted.visibility == View.VISIBLE)) return false

        val target = weightTarget(totalBales)
        if (weighedBales >= target) return false

        val remainingTotal = totalBales - receivedBales
        val remainingWeighted = target - weighedBales

        return remainingTotal <= remainingWeighted && !chkWeight.isChecked
    }

    // ------------------------------------------------------------------
    // SAVE
    // ------------------------------------------------------------------

    private fun onSaveClicked() {

        var weightText = edtWeight.text.toString()

        if (chkWeight.isChecked) {
            if (WEIGHT_LOCKED_TO_MARKED) {
                currWt = markedWt
            } else if (weightText.isNotEmpty()) {
                if (weightText == ".") {
                    edtWeight.error = "Please Enter valid Weight"
                    edtWeight.requestFocus()
                    return
                }
                if (Regex("^\\.\\d+$").matches(weightText)) weightText = "0$weightText"
                else if (Regex("^\\d+\\.$").matches(weightText)) weightText += "0"
                edtWeight.setText(weightText)
                currWt = weightText.toDoubleOrNull() ?: 0.0
            }
        } else {
            currWt = markedWt
        }

        when {

            edtBale.text.toString().isEmpty() ->
                edtBale.error = "Enter The Case Number"

            chkWeight.isChecked && weightText.isEmpty() ->
                edtWeight.error = "Enter The Weight"

            chkWeight.isChecked && currWt > MAX_WEIGHT -> {
                edtWeight.error = "Weight Allows Only Below 300"
                edtWeight.requestFocus()
                if (!WEIGHT_LOCKED_TO_MARKED) edtWeight.setText("")
            }

            chkWeight.isChecked && currWt <= 0 -> {
                edtWeight.error = "0 Value Not Allowed In Weight"
                edtWeight.requestFocus()
                if (!WEIGHT_LOCKED_TO_MARKED) edtWeight.setText("")
            }

            mustWeighThisBale() ->
                showError("Please Complete Weighment Bale")

            !rbTwenty.isChecked && !rbHundred.isChecked ->
                showError("Please Select Any Weighment Type")

            currentBale == null || txtMarked.text.isNullOrEmpty() || txtGrade.text.isNullOrEmpty() ->
                Toast.makeText(this, "Data is Loading", Toast.LENGTH_SHORT).show()

            chkWeight.isChecked && currWt > markedWt ->
                showError("Weight should not exist from marked weight")

            else -> {

                val weightLoss = currWt * 1.5 / 100
                val minimumWeight = markedWt - weightLoss

                if (chkWeight.isChecked && currWt < minimumWeight) {

                    // old rule: only a supervisor (rights "A") may accept a weight loss
                    if (sessionManager.getUserRights() == "A") {
                        showDialog(
                            "Weight loss is more than 1.5%, Do you want accept the weightloss?",
                            positive = "Yes",
                            negative = "No",
                            onPositive = { saveBale() }
                        )
                    } else {
                        showError(
                            "Weight loss is more than 1.5%, You don't have the right to accept " +
                                    "please inform to supervisor/buyer"
                        )
                    }

                } else {
                    saveBale()
                }
            }
        }
    }

    private fun saveBale() {

        val bale = currentBale ?: return

        val deviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

        viewModel.saveBale(
            baleNo = bale.baleNumber,
            receiptWeight = currWt,
            weightStatus = if (chkWeight.isChecked) "Y" else "N",
            weighType = weighType,
            userId = sessionManager.getUserId().orEmpty(),
            deviceId = deviceId
        )
    }

    private fun onSaved(isFirstBale: Boolean) {

        clearFields()

        // saved on the device first -> upload now if the network is good, else it waits
        SyncManager.requestSync(applicationContext)

        val online = NetworkMonitor.isHighQuality.value == true
        Toast.makeText(
            this,
            if (online) "Bale saved - uploading"
            else "Bale saved on the device - it will upload when the network is available",
            Toast.LENGTH_SHORT
        ).show()

        if (isFirstBale) {
            lockWeighType()

            viewModel.refreshWeighTypeFromServer(
                sessionManager.getOrganizationCode().orEmpty(),
                weighType
            )
        }
    }

    private fun clearFields() {

        edtBale.error = null
        edtWeight.error = null

        edtBale.setText("")
        edtWeight.setText("")

        txtMarked.text = ""
        txtGrade.text = ""
        txtSubCode.text = ""

        currentBale = null
        markedWt = 0.0
        currWt = 0.0

        edtBale.isEnabled = true
        btnSave.text = "Save"

        edtBale.requestFocus()
    }

    // ------------------------------------------------------------------
    // DIALOGS
    // ------------------------------------------------------------------

    private fun showDialog(
        message: String,
        positive: String = "OK",
        negative: String? = null,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null
    ) {

        val builder = AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(positive) { dialog, _ ->
                dialog.dismiss()
                onPositive?.invoke()
            }

        if (negative != null) {
            builder.setNegativeButton(negative) { dialog, _ ->
                dialog.dismiss()
                onNegative?.invoke()
            }
        }

        builder.show()
    }

    private fun showError(message: String) = showDialog(message)

    private fun openForcedWeighDialog() {
        showDialog("Please Complete Weighment Bale") {
            btnSave.text = "Save"
            chkWeight.isChecked = true
            chkWeight.isEnabled = true
            if (!WEIGHT_LOCKED_TO_MARKED) {
                edtWeight.setText("")
                edtWeight.requestFocus()
            }
        }
    }

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}