package com.united.digitaldispatch.CaseInquiry

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.MasterDataViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.MasterDataViewModelFactory
import com.united.digitaldispatch.Dispatch.models.BaleInfo
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.R
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaseInquiry : AppCompatActivity() {

    // ViewModel
    private lateinit var masterDataViewModel: MasterDataViewModel

    // Input
    private lateinit var edtBaleNo: EditText

    // Buttons
    private lateinit var btnClear: Button
    private lateinit var btnBack: ImageView

    // Case details
    private lateinit var txtGrade: TextView
    private lateinit var txtWeight: TextView
    private lateinit var txtRate: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtPurchaseDate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_case_inquiry)

        initializeViewModel()
        initializeViews()
        setHeaderDetails()
        setupClickListeners()
        observeBaleDetails()
    }

    // Initialize existing ViewModel and Repository
    private fun initializeViewModel() {

        val database = AppDatabase.getInstance(applicationContext)

        val repository = MasterDataRepository(
            ApiClient.instance,
            database
        )

        masterDataViewModel = ViewModelProvider(
            this,
            MasterDataViewModelFactory(repository)
        )[MasterDataViewModel::class.java]
    }

    private fun initializeViews() {

        edtBaleNo = findViewById(R.id.edt_bale_no)

        btnClear = findViewById(R.id.btn_clear)
        btnBack = findViewById(R.id.back_fab)

        txtGrade = findViewById(R.id.txt_tb_grade)
        txtWeight = findViewById(R.id.txt_weight)
        txtRate = findViewById(R.id.txt_rate)
        txtStatus = findViewById(R.id.txt_status)
        txtPurchaseDate = findViewById(R.id.txt_purchase_date)
    }

    private fun setHeaderDetails() {

        val session = SessionManager(applicationContext)

        findViewById<TextView>(R.id.txt_title).text = "Case Inquiry"

        findViewById<TextView>(R.id.sub_tittle).text =
            "(Details Info)"

        findViewById<TextView>(R.id.txt_org_code).text =
            session.getOrganizationCode().orEmpty()

        findViewById<TextView>(R.id.txt_date).text =
            SimpleDateFormat(
                "dd-MM-yyyy",
                Locale.getDefault()
            ).format(Date())
    }

    private fun setupClickListeners() {

        btnBack.setOnClickListener {
            finish()
        }

        btnClear.setOnClickListener {
            clearFields()
        }

        // Search when user presses Done or scanner sends Enter
        edtBaleNo.setOnEditorActionListener { _, actionId, event ->

            val isDone = actionId == EditorInfo.IME_ACTION_DONE

            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_UP

            if (isDone || isEnter) {
                searchBale()
                true
            } else {
                false
            }
        }
    }

    private fun searchBale() {

        val baleNumber = edtBaleNo.text.toString().trim()

        masterDataViewModel.searchBale(baleNumber)
    }

    // Observe search result and error
    private fun observeBaleDetails() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    masterDataViewModel.baleInfo.collect { result ->

                        if (result != null) {
                            displayBaleDetails(result)
                        }
                    }
                }

                launch {
                    masterDataViewModel.baleError.collect { error ->

                        if (error != null) {

                            Toast.makeText(
                                this@CaseInquiry,
                                error,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    // Display Room database result
    private fun displayBaleDetails(baleInfo: BaleInfo) {

        txtGrade.text = baleInfo.grade
        txtWeight.text = baleInfo.markedWeight
        txtRate.text = baleInfo.price
        txtStatus.text = baleInfo.status
        txtPurchaseDate.text = baleInfo.purchaseDate
    }

    private fun clearFields() {

        edtBaleNo.text.clear()

        txtGrade.text = ""
        txtWeight.text = ""
        txtRate.text = ""
        txtStatus.text = ""
        txtPurchaseDate.text = ""

        masterDataViewModel.clearBaleSearch()

        edtBaleNo.requestFocus()
    }
}