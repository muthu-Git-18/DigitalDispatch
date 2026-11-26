
package com.united.digitaldispatch.PSWReceipt

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
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.PswReceiptRepository
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptViewModelFactory
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptDetailEvent
import com.united.digitaldispatch.Apiservice.viewmodel.PswReceiptSyncState
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.PswReceiptDetailEntity
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
        private const val MAX_WEIGHT = 200.0
    }

    private val COLOR_COUNT_OK = Color.parseColor("#2E7D32")
    private val COLOR_COUNT_PENDING = Color.parseColor("#C0392B")

    private lateinit var sessionManager: SessionManager
    private lateinit var shipmentNo: String

    private lateinit var rbTwenty: RadioButton
    private lateinit var rbHundred: RadioButton
    private lateinit var chkWeight: CheckBox

    private lateinit var edtBale: EditText
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
        setupWeightFilter()
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

        findViewById<TextView>(R.id.txt_title).text = "PSW Receipt"

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

        llWeight.visibility = View.GONE
        llWeighted.visibility = View.GONE
        btnSave.visibility = View.GONE

        edtBale.requestFocus()
    }

    private fun setupWeightFilter() {
        val decimalFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val result = dest.substring(0, dstart) +
                    source.subSequence(start, end) +
                    dest.substring(dend)

            if (Regex("^\\d{0,3}(\\.\\d?)?$").matches(result)) {
                null
            } else {
                ""
            }
        }

        edtWeight.filters = arrayOf(
            InputFilter.LengthFilter(5),
            decimalFilter
        )
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

        btnView.setOnClickListener {
            startActivity(
                android.content.Intent(this, PSWReceiptView::class.java)
            )
        }

        btnSummary.setOnClickListener {
            if (receivedBales == 0) {
                showError("No PSW Receipt Bales Are Added")
            } else {
                startActivity(
                    android.content.Intent(this, PSWReceiptSummary::class.java)
                        .putExtra(EXTRA_SHIPMENT_NO, shipmentNo)
                )
            }
        }
    }

    private fun setupBaleInput() {

        edtBale.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) = Unit

            override fun afterTextChanged(s: Editable?) {

                scanRunnable?.let { handler.removeCallbacks(it) }

                val value = s?.toString().orEmpty().trim()

                if (value.length == 13 || value.length == 14) {
                    val runnable = Runnable {
                        onBaleEntered(value)
                    }

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
                    onBaleEntered(value)
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

                launch {
                    viewModel.totalBales.collect {
                        totalBales = it
                        refreshCounts()
                    }
                }

                launch {
                    viewModel.receivedBales.collect {
                        receivedBales = it
                        refreshCounts()
                    }
                }

                launch {
                    viewModel.weighedBales.collect {
                        weighedBales = it
                        refreshCounts()
                    }
                }

                launch {
                    viewModel.pendingBales.collect {
                        pendingBales = it
                        refreshCounts()
                    }
                }

                launch {
                    viewModel.events.collect {
                        handleEvent(it)
                    }
                }

                launch {
                    viewModel.baleSyncState.collect { state ->

                        when (state) {

                            is PswReceiptSyncState.Loading -> {
                                balesLoading = true
                            }

                            is PswReceiptSyncState.Success -> {
                                balesLoading = false
                                viewModel.resolveSavedWeighType()
                            }

                            is PswReceiptSyncState.Error -> {
                                balesLoading = false

                                if (totalBales == 0) {
                                    showError(
                                        "Unable to load PSW bales: ${state.message}"
                                    )
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
            0,
            base.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        if (pendingBales > 0) {
            val start = text.length
            text.append(" ($pendingBales pending)")

            text.setSpan(
                ForegroundColorSpan(COLOR_COUNT_PENDING),
                start,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        txtReceived.text = text
        txtWeighted.text = "$weighedBales / ${weightTarget(totalBales)}"
    }

    private fun weightTarget(total: Int): Int {
        return if (total > 5) {
            Math.round(total / 100.0 * 20).toInt()
        } else {
            1
        }
    }

    private fun handleEvent(event: PswReceiptDetailEvent) {

        when (event) {

            is PswReceiptDetailEvent.BaleNotFound -> {
                lookupInFlight = false
                edtBale.error = "No Bale Number Found"
                edtBale.setText("")
                edtBale.requestFocus()
            }

            is PswReceiptDetailEvent.BaleFound -> {
                lookupInFlight = false
                onBaleFound(event.bale)
            }

            is PswReceiptDetailEvent.Saved -> {
                onSaved(event.isFirstBale)
            }

            is PswReceiptDetailEvent.InitialWeighType -> {
                applyWeighType(event.type, true)
            }

            is PswReceiptDetailEvent.WeighTypeResolved -> {
                applyWeighType(event.type, true)

                if (event.changed) {
                    val label = if (event.type == WEIGH_20) {
                        "20% Weighment"
                    } else {
                        "100% Weighment"
                    }

                    showMessage(
                        "This shipment is already set to $label. Weighment type changed."
                    )
                }
            }

            is PswReceiptDetailEvent.Error -> {
                lookupInFlight = false
                Toast.makeText(
                    this,
                    event.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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

        if (lock) {
            rbTwenty.isEnabled = false
            rbHundred.isEnabled = false
        }
    }

    private fun onBaleEntered(bale: String) {

        if (balesLoading) {
            Toast.makeText(
                this,
                "Loading PSW bales, please wait",
                Toast.LENGTH_SHORT
            ).show()

            edtBale.setText("")
            return
        }

        if (lookupInFlight || !edtBale.isEnabled) return

        if (!isValidBaleFormat(bale)) {
            showError("Invalid Bale Number")
            clearFields()
            return
        }

        if (!rbTwenty.isChecked && !rbHundred.isChecked) {
            showError("Please Select Any Weighment Type")
            clearFields()
            return
        }

        lookupInFlight = true
        viewModel.lookupBale(bale)
    }

    private fun isValidBaleFormat(bale: String): Boolean {

        val org = sessionManager.getModuleType().orEmpty()

        if (bale.length != 13 && bale.length != 14) return false

        val digitsOnly = bale.substring(7, 13).all { it.isDigit() }

        return if (bale.length == 13) {
            (digitsOnly && org != "WH") ||
                    ((org == "GLT" || org == "WH" || org == "PPD") &&
                            bale[7] == 'P')
        } else {
            (digitsOnly && org != "WH") ||
                    org == "GLT" ||
                    (org == "WH" && bale[8] == 'P')
        }
    }

    private fun onBaleFound(bale: PswReceiptDetailEntity) {

        if (bale.status == PswReceiptDetailEntity.STATUS_RECEIVED) {

            showDialog(
                message = "Do you want to update this Bale Number?",
                positive = "Yes",
                negative = "No",
                onPositive = {
                    btnSave.text = "Update"
                    fillFromBale(bale)
                },
                onNegative = {
                    clearFields()
                }
            )

            return
        }

        fillFromBale(bale)

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
        txtSubCode.text = bale.fromSubinventoryCode.orEmpty()

        edtBale.isEnabled = false
        edtWeight.requestFocus()
    }

    private fun mustWeighThisBale(): Boolean {

        if (!(rbTwenty.isChecked && llWeighted.visibility == View.VISIBLE)) {
            return false
        }

        val target = weightTarget(totalBales)

        if (weighedBales >= target) return false

        val remainingTotal = totalBales - receivedBales
        val remainingWeighted = target - weighedBales

        return remainingTotal <= remainingWeighted && !chkWeight.isChecked
    }

    private fun onSaveClicked() {

        var weightText = edtWeight.text.toString()

        if (chkWeight.isChecked && weightText.isNotEmpty()) {

            if (weightText == ".") {
                edtWeight.error = "Please Enter valid Weight"
                edtWeight.requestFocus()
                return
            }

            if (Regex("^\\.\\d+$").matches(weightText)) {
                weightText = "0$weightText"
            } else if (Regex("^\\d+\\.$").matches(weightText)) {
                weightText += "0"
            }

            edtWeight.setText(weightText)
            currWt = weightText.toDoubleOrNull() ?: 0.0

        } else {
            currWt = markedWt
        }

        when {

            edtBale.text.toString().isEmpty() -> {
                edtBale.error = "Enter The Bale Number"
            }

            chkWeight.isChecked && weightText.isEmpty() -> {
                edtWeight.error = "Enter The Weight"
            }

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

            currentBale == null -> {
                Toast.makeText(this, "Data is Loading", Toast.LENGTH_SHORT).show()
            }

            mustWeighThisBale() -> {
                showError("Please Complete Weighment Bale")
            }

            !rbTwenty.isChecked && !rbHundred.isChecked -> {
                showError("Please Select Any Weighment Type")
            }

            chkWeight.isChecked && currWt > markedWt -> {
                showError("Weight should not exceed marked weight")
            }

            else -> {

                val weightLoss = currWt * 1.5 / 100
                val minimumWeight = markedWt - weightLoss

                if (chkWeight.isChecked && currWt < minimumWeight) {

                    showDialog(
                        message = "Weight loss is more than 1.5%. Do you want to accept the weight loss?",
                        positive = "Yes",
                        negative = "No",
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

        Toast.makeText(
            this,
            "PSW Receipt bale saved locally",
            Toast.LENGTH_SHORT
        ).show()

        if (isFirstBale) {
            rbTwenty.isEnabled = false
            rbHundred.isEnabled = false

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

    private fun showError(message: String) {
        showDialog(message)
    }

    private fun showMessage(message: String) {
        showDialog(message)
    }

    private fun openForcedWeighDialog() {

        showDialog("Please Complete Weighment Bale") {
            btnSave.text = "Save"
            chkWeight.isChecked = true
            chkWeight.isEnabled = true
            edtWeight.setText("")
            edtWeight.requestFocus()
        }
    }

    private fun fmt(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
}
