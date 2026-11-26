package com.united.digitaldispatch.Receipt

import android.content.Intent
import android.graphics.Color
import android.os.Build
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
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.ReceiptRepository
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptDetailEvent
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptSyncState
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.ReceiptViewModelFactory
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import com.united.digitaldispatch.sync.NetworkMonitor
import com.united.digitaldispatch.sync.ReceiptSignalRService        // NEW
import com.united.digitaldispatch.sync.SyncManager                  // NEW
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptDetails : AppCompatActivity() {

    companion object {
        private const val WEIGH_20 = "TWT"
        private const val WEIGH_100 = "HND"
        private const val SCAN_DEBOUNCE_MS = 300L
        private const val MAX_WEIGHT = 200.0
    }

    private val COLOR_COUNT_OK = Color.parseColor("#2E7D32")       // green
    private val COLOR_COUNT_PENDING = Color.parseColor("#C0392B")  // red

    private lateinit var sessionManager: SessionManager
    private lateinit var shipmentNo: String

    private lateinit var rbTwenty: RadioButton
    private lateinit var rbHundred: RadioButton
    private lateinit var chkWeight: CheckBox
    private lateinit var edtBale: EditText
    private lateinit var edtWeight: EditText
    private lateinit var txtGrade: TextView
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

    private var currentBale: ReceiptDetailEntity? = null
    private var markedWt = 0.0
    private var currWt = 0.0
    private var weighType = ""

    private var totalBales = 0
    private var receivedBales = 0
    private var weighedBales = 0

    private var pendingBales = 0

    private var balesLoading = false

    // NEW: live sync must start only AFTER the bales are downloaded, otherwise a change
    // could be applied to a row that does not exist yet and the bookmark would move past it
    private var liveSyncStarted = false

    private val viewModel: ReceiptViewModel by viewModels {
        ReceiptViewModelFactory(
            ReceiptRepository(ApiClient.instance, AppDatabase.getInstance(applicationContext))
        )
    }

    // ------------------------------------------------------------------
    // LIFECYCLE
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_details)

        shipmentNo = intent.getStringExtra(Receipt.EXTRA_SHIPMENT_NO).orEmpty()
        if (shipmentNo.isBlank()) {
            Toast.makeText(this, "Shipment missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sessionManager = SessionManager(this)
        viewModel.setShipment(shipmentNo)

        initViews()
        setupWeightFilter()
        setupListeners()
        setupBaleInput()
        observe()

        viewModel.resolveSavedWeighType()
        viewModel.downloadBales(shipmentNo)
    }

    // NEW
    override fun onStart() {
        super.onStart()
        SyncManager.setActiveShipment(shipmentNo, SyncManager.SyncModule.RECEIPT)
    }

    // NEW
    override fun onStop() {
        super.onStop()
        SyncManager.setActiveShipment(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanRunnable?.let { handler.removeCallbacks(it) }
        // NEW: leaving the shipment for good -> leave its group / stop the live connection
        if (isFinishing) ReceiptSignalRService.stop(this, shipmentNo)
    }

    // NEW
    /** return sync: pull what other devices did once, then listen for live changes */
    private fun startLiveSync() {
        if (liveSyncStarted) return
        liveSyncStarted = true
        SyncManager.pullReceiptChanges(applicationContext, shipmentNo, source = "receipt-open")
        ReceiptSignalRService.start(this, shipmentNo)
    }

    private fun initViews() {
        findViewById<TextView>(R.id.txt_org_code).text = sessionManager.getOrganizationCode().orEmpty()
        findViewById<TextView>(R.id.txt_date).text =
            SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        findViewById<TextView>(R.id.txt_shipmentNo).text = shipmentNo

        orgType().takeIf { it.isNotBlank() }?.let {
            findViewById<TextView>(R.id.txt_title).text = "Receipt ($it)"
        }

        rbTwenty = findViewById(R.id.rb_twentyPercent)
        rbHundred = findViewById(R.id.rb_hundredPercent)
        chkWeight = findViewById(R.id.chk_weight)
        edtBale = findViewById(R.id.edt_baleNumber)
        edtWeight = findViewById(R.id.ed_weight)
        txtGrade = findViewById(R.id.txt_grade)
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

        llWeight.visibility = View.GONE
        llWeighted.visibility = View.GONE
        btnSave.visibility = View.GONE
        edtBale.requestFocus()
    }

    // up to 3 digits + 1 decimal (max 5 chars), same idea as the old DecimalDigitsInputFilter(5,1)
    private fun setupWeightFilter() {
        val decimalFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val result = dest.substring(0, dstart) + source.subSequence(start, end) + dest.substring(dend)
            if (Regex("^\\d{0,3}(\\.\\d?)?$").matches(result)) null else ""
        }
        edtWeight.filters = arrayOf(InputFilter.LengthFilter(5), decimalFilter)
    }

    // ------------------------------------------------------------------
    // LISTENERS
    // ------------------------------------------------------------------

    private fun setupListeners() {

        rbTwenty.setOnClickListener { if (rbTwenty.isChecked) onWeighTypeSelected(WEIGH_20) }
        rbHundred.setOnClickListener { if (rbHundred.isChecked) onWeighTypeSelected(WEIGH_100) }

        chkWeight.setOnCheckedChangeListener { _, checked ->
            val vis = if (checked) View.VISIBLE else View.GONE
            llWeight.visibility = vis
            btnSave.visibility = vis
            if (!checked) {
                edtWeight.setText("")
                currWt = 0.0
            }
        }

        btnSave.setOnClickListener { onSaveClicked() }
        btnClear.setOnClickListener { clearTheFields() }
        btnBack.setOnClickListener { finish() }

        btnView.setOnClickListener {
            startActivity(
                Intent(this, ReceiptView::class.java)
                    .putExtra(ReceiptView.EXTRA_SHIPMENT_NO, shipmentNo)
            )
        }

        btnSummary.setOnClickListener {
            if (receivedBales == 0) {
                openErrorDialog("No Receipt Bales Are Added")
            } else {
                startActivity(
                    Intent(this, ReceiptSummary::class.java)
                        .putExtra(Receipt.EXTRA_SHIPMENT_NO, shipmentNo)
                )
            }
        }
    }

    // scanner types the bale very fast -> wait a moment so a 14 digit bale is not read at 13
    private fun setupBaleInput() {

        edtBale.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                scanRunnable?.let { handler.removeCallbacks(it) }
                val value = s?.toString().orEmpty().trim()
                if (value.length == 13 || value.length == 14) {
                    val r = Runnable { onBaleEntered(value) }
                    scanRunnable = r
                    handler.postDelayed(r, SCAN_DEBOUNCE_MS)
                }
            }
        })

        edtBale.setOnEditorActionListener { _, actionId, event ->
            val isEnter = actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_NEXT ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isEnter) {
                scanRunnable?.let { handler.removeCallbacks(it) }
                val v = edtBale.text.toString().trim()
                if (v.isNotEmpty()) onBaleEntered(v)
                true
            } else false
        }
    }

    // ------------------------------------------------------------------
    // OBSERVE
    // ------------------------------------------------------------------

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
                            is ReceiptSyncState.Loading -> balesLoading = true
                            is ReceiptSyncState.Success -> {
                                balesLoading = false
                                viewModel.resolveSavedWeighType()   // header/bale type may now be known
                                startLiveSync()                     // NEW
                            }
                            is ReceiptSyncState.Error -> {
                                balesLoading = false
                                if (totalBales == 0) {
                                    openErrorDialog("Unable to load bales: ${state.message}")
                                } else {
                                    Toast.makeText(this@ReceiptDetails, "Showing saved bales (offline)", Toast.LENGTH_SHORT).show()
                                    startLiveSync()                 // NEW: saved bales exist, sync can start
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

        // received count -> green
        text.setSpan(ForegroundColorSpan(COLOR_COUNT_OK), 0, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // not uploaded yet -> red
        if (pendingBales > 0) {
            val start = text.length
            text.append(" ($pendingBales pending)")
            text.setSpan(ForegroundColorSpan(COLOR_COUNT_PENDING), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        txtReceived.text = text
        txtWeighted.text = "$weighedBales / ${weightTarget(totalBales)}"
    }
    // 20 % of the bales (min 1 when 5 bales or fewer) -- same as the old screen
    private fun weightTarget(total: Int): Int =
        if (total > 5) Math.round(total / 100.0 * 20).toInt() else 1

    private fun handleEvent(e: ReceiptDetailEvent) {
        when (e) {

            is ReceiptDetailEvent.BaleNotFound -> {
                lookupInFlight = false
                edtBale.error = "No Bale Number Found"
                edtBale.setText("")
                edtBale.requestFocus()
                btnSave.text = "Save"
            }

            is ReceiptDetailEvent.BaleFound -> {
                lookupInFlight = false
                onBaleFound(e.bale)
            }

            is ReceiptDetailEvent.Saved -> onSaved(e.isFirstBale)

            is ReceiptDetailEvent.InitialWeighType -> applyWeighType(e.type, lock = true)

            is ReceiptDetailEvent.WeighTypeResolved -> {
                applyWeighType(e.type, lock = true)
                if (e.changed) {
                    val label = if (e.type == WEIGH_20) "20% Weighment" else "100% Weighment"
                    showDialog("This shipment is already set to $label. Weighment type changed.")
                }
            }

            is ReceiptDetailEvent.Error -> {
                lookupInFlight = false
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------
    // WEIGHMENT TYPE (radio)
    // ------------------------------------------------------------------

    private fun onWeighTypeSelected(type: String) {
        weighType = type
        if (type == WEIGH_20) {
            chkWeight.isChecked = false
            chkWeight.isEnabled = true
            llWeighted.visibility = View.VISIBLE
        } else {
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
    // BALE SCAN + VALIDATION
    // ------------------------------------------------------------------

    private fun onBaleEntered(bale: String) {

        if (balesLoading) {
            Toast.makeText(this, "Loading bales, please wait", Toast.LENGTH_SHORT).show()
            edtBale.setText("")
            return
        }

        if (lookupInFlight || !edtBale.isEnabled) return

        if (!isValidBaleFormat(bale)) {
            openErrorDialog("Invalid Bale Number")
            clearTheFields()
            return
        }

        if (!rbTwenty.isChecked && !rbHundred.isChecked) {
            openErrorDialog("Please Select Any Weighment Type")
            clearTheFields()
            return
        }

        lookupInFlight = true
        viewModel.lookupBale(bale)
    }

    // same rules as the old app
    private fun isValidBaleFormat(b: String): Boolean {
        val org = orgType()
        if (b.length != 13 && b.length != 14) return false
        val digitsOnly = b.substring(7, 13).all { it.isDigit() }

        return if (b.length == 13) {
            (digitsOnly && org != "WH") ||
                    ((org == "GLT" || org == "WH" || org == "PPD") && b[7] == 'P')
        } else {
            (digitsOnly && org != "WH") ||
                    org == "GLT" ||
                    (org == "WH" && b[8] == 'P')
        }
    }

    private fun onBaleFound(bale: ReceiptDetailEntity) {

        if (bale.status == ReceiptDetailEntity.STATUS_RECEIVED) {
            // already received -> update flow
            showDialog(
                "Do you want to update this Bale Number",
                positive = "Yes", negative = "No",
                onPositive = {
                    btnSave.text = "Update"
                    fillFromBale(bale)
                },
                onNegative = { clearTheFields() }
            )
            return
        }

        // first time entry
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

    private fun fillFromBale(bale: ReceiptDetailEntity) {
        currentBale = bale
        markedWt = bale.markedWt
        txtMarked.text = fmt(bale.markedWt)
        txtGrade.text = bale.grade.orEmpty()
        edtBale.isEnabled = false
        edtWeight.requestFocus()
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

        var w = edtWeight.text.toString()

        if (chkWeight.isChecked && w.isNotEmpty()) {
            if (w == ".") {
                edtWeight.error = "Please Enter valid Weight"
                edtWeight.requestFocus()
                return
            }
            if (Regex("^\\.\\d+$").matches(w)) w = "0$w"
            else if (Regex("^\\d+\\.$").matches(w)) w += "0"
            edtWeight.setText(w)
            currWt = w.toDouble()
        } else {
            currWt = markedWt
        }

        when {
            edtBale.text.toString().isEmpty() ->
                edtBale.error = "Enter The Bale Number"

            chkWeight.isChecked && w.isEmpty() ->
                edtWeight.error = "Enter The Weight"

            chkWeight.isChecked && currWt > MAX_WEIGHT -> {
                edtWeight.error = "Weight Allows Only Below 200"
                edtWeight.requestFocus()
                edtWeight.setText("")
            }

            chkWeight.isChecked && currWt <= 0 -> {
                edtWeight.error = "0 Value Not Allowed In Weight"
                edtWeight.requestFocus()
                edtWeight.setText("")
            }

            currentBale == null || txtMarked.text.isNullOrEmpty() ->
                Toast.makeText(this, "Data is Loading", Toast.LENGTH_SHORT).show()

            mustWeighThisBale() ->
                openErrorDialog("Please Complete Weighment Bale")

            !rbTwenty.isChecked && !rbHundred.isChecked ->
                openErrorDialog("Please Select Any Weighment Type")

            chkWeight.isChecked && currWt > markedWt ->
                openErrorDialog("Weight should not exist from marked weight")

            else -> {
                // same formula as the old screen
                val weightLoss = currWt * 1.5 / 100
                val wtLowLimit = markedWt - weightLoss

                if (chkWeight.isChecked && currWt < wtLowLimit) {
                    showDialog(
                        "Weight loss is more than 1.5%,Do you want accept the weightloss?",
                        positive = "Yes", negative = "No",
                        onPositive = { saveBale() }
                    )
                } else {
                    saveBale()
                }
            }
        }
    }

    private fun saveBale() {
        val bale = currentBale ?: return

        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()



        viewModel.saveBale(
            baleNo = bale.baleNumber,
            receiptWeight = currWt,
            weightStatus = if (chkWeight.isChecked) "Y" else "N",
            weighType = weighType,
            userId = userId(),
            deviceId = deviceId
        )
    }

    private fun onSaved(isFirstBale: Boolean) {
        clearTheFields()


        // NEW: saved on the device first -> upload now if the network is good, else it waits
        SyncManager.requestSync(applicationContext)

        val online = NetworkMonitor.isHighQuality.value == true
        Toast.makeText(
            this,
            if (online) "Bale saved - uploading"
            else "Bale saved on the device - it will upload when the network is available",
            Toast.LENGTH_SHORT
        ).show()

        if (isFirstBale) {
            // fix the radio now; then re-check the header from the server (another device may have set it)
            lockWeighType()
            viewModel.refreshWeighTypeFromServer(
                sessionManager.getOrganizationCode().orEmpty(),
                weighType
            )
        }
    }

    private fun clearTheFields() {
        edtBale.error = null
        edtWeight.error = null
        edtBale.setText("")
        edtWeight.setText("")
        txtMarked.text = ""
        txtGrade.text = ""
        currentBale = null
        markedWt = 0.0
        currWt = 0.0
        edtBale.isEnabled = true
        edtBale.requestFocus()
        btnSave.text = "Save"
    }

    // ------------------------------------------------------------------
    // DIALOGS  (swap for your themed dialog if you like)
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
            .setPositiveButton(positive) { d, _ -> d.dismiss(); onPositive?.invoke() }

        if (negative != null) {
            builder.setNegativeButton(negative) { d, _ -> d.dismiss(); onNegative?.invoke() }
        }
        builder.show()
    }

    private fun openErrorDialog(message: String) =
        showDialog(message) {
            edtWeight.setText("")
            edtWeight.requestFocus()
        }

    // "Please Complete Weighment Bale" -> forces the weight check box on
    private fun openForcedWeighDialog() =
        showDialog("Please Complete Weighment Bale") {
            btnSave.text = "Save"
            chkWeight.isChecked = true
            chkWeight.isEnabled = true
            edtWeight.setText("")
            edtWeight.requestFocus()
        }

    // ------------------------------------------------------------------
    // HELPERS  -- adjust these three to your SessionManager
    // ------------------------------------------------------------------

    private fun orgType(): String = sessionManager.getModuleType().orEmpty()   // GLT / WH / PPD ...
    private fun userId(): String = sessionManager.getUserId().orEmpty()
    private fun deviceId(): String = Build.MODEL.orEmpty()

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}