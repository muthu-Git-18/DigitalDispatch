
package com.united.digitaldispatch.Stock

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.MasterDataViewModel
import com.united.digitaldispatch.Apiservice.viewmodel.MasterDataViewModelFactory
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Stock : AppCompatActivity() {

    private lateinit var viewModel: MasterDataViewModel

    private lateinit var cropSelector: TextView
    private lateinit var varietySelector: TextView

    private lateinit var stockSummaryAdapter: StockSummaryAdapter
    private lateinit var stockHeaderAdapter: StockHeaderAdapter

    private lateinit var btnView: Button
    private lateinit var btnClear: Button

    private lateinit var txtNoData: TextView
    private lateinit var txtTotalBales: TextView
    private lateinit var txtTotalQuantity: TextView

    private val cropItems = mutableListOf("Select Crop")
    private val varietyItems = mutableListOf("Select Variety")

    private var selectedCrop: String? = null
    private var selectedVariety: String? = null

    private var cropPopup: PopupWindow? = null
    private var varietyPopup: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock)

        initializeViews()
        initializeViewModel()
        setupSelectors()
        setupButtons()
        setHeaderDetails()
        observeViewModel()

        viewModel.loadStockScreen()
    }

    private fun initializeViews() {
        cropSelector = findViewById(R.id.spinner_crop_year)
        varietySelector = findViewById(R.id.spinner_variety_year)

        btnView = findViewById(R.id.btn_clear)
        btnClear = findViewById(R.id.btn_createHeader)

        txtNoData = findViewById(R.id.txt_no_data)
        txtTotalBales = findViewById(R.id.txt_tot_count)
        txtTotalQuantity = findViewById(R.id.txt_tot_bales)

        stockHeaderAdapter = StockHeaderAdapter()
        stockSummaryAdapter = StockSummaryAdapter()

        findViewById<RecyclerView>(R.id.rv_header).apply {
            layoutManager = LinearLayoutManager(this@Stock)
            adapter = stockHeaderAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<RecyclerView>(R.id.rv_item).apply {
            layoutManager = LinearLayoutManager(this@Stock)
            adapter = stockSummaryAdapter
            isNestedScrollingEnabled = true
        }

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        cropSelector.text = cropItems.first()
        varietySelector.text = varietyItems.first()
    }

    private fun initializeViewModel() {
        val repository = MasterDataRepository(
            ApiClient.instance,
            AppDatabase.getInstance(applicationContext)
        )

        viewModel = ViewModelProvider(
            this,
            MasterDataViewModelFactory(repository)
        )[MasterDataViewModel::class.java]
    }

    private fun setupSelectors() {
        cropSelector.setOnClickListener {
            showCustomPopup(
                anchor = cropSelector,
                items = cropItems,
                currentValue = selectedCrop,
                onSelected = { value ->
                    if (selectedCrop != value) {
                        selectedCrop = value
                        cropSelector.text = value ?: "Select Crop"

                        // A variety from the previous crop is no longer valid.
                        selectedVariety = null
                        varietySelector.text = "Select Variety"

                        viewModel.loadStockVarieties(selectedCrop)
                    }
                }
            )
        }

        varietySelector.setOnClickListener {
            showCustomPopup(
                anchor = varietySelector,
                items = varietyItems,
                currentValue = selectedVariety,
                onSelected = { value ->
                    selectedVariety = value
                    varietySelector.text = value ?: "Select Variety"
                }
            )
        }
    }

    private fun showCustomPopup(
        anchor: View,
        items: List<String>,
        currentValue: String?,
        onSelected: (String?) -> Unit
    ) {
        if (items.isEmpty()) return

        val listView = ListView(this).apply {
            divider = ColorDrawable(Color.parseColor("#E4D7C0"))
            dividerHeight = 1
            selector = ColorDrawable(Color.parseColor("#F0E2C6"))
            setBackgroundColor(Color.TRANSPARENT)

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = object : ArrayAdapter<String>(
                this@Stock,
                R.layout.item_custom_popup,
                android.R.id.text1,
                items
            ) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    val row = super.getView(position, convertView, parent) as TextView
                    row.text = items[position]
                    row.setTypeface(
                        null,
                        if (items[position] == currentValue) {
                            android.graphics.Typeface.BOLD
                        } else {
                            android.graphics.Typeface.NORMAL
                        }
                    )
                    return row
                }
            }
        }

        val popupWidth = anchor.width.coerceAtLeast(
            resources.displayMetrics.widthPixels / 3
        )

        val maxPopupHeight = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        val desiredHeight = (items.size * (48 * resources.displayMetrics.density)).toInt()
        val popupHeight = desiredHeight.coerceAtMost(maxPopupHeight)

        val popup = PopupWindow(
            listView,
            popupWidth,
            popupHeight,
            true
        ).apply {
            setBackgroundDrawable(
                resources.getDrawable(R.drawable.bg_custom_popup, theme)
            )
            elevation = 10f * resources.displayMetrics.density
            isOutsideTouchable = true
            setOnDismissListener {
                if (cropPopup === this) cropPopup = null
                if (varietyPopup === this) varietyPopup = null
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val value = items[position]
            popup.dismiss()
            onSelected(
                if (position == 0) null else value
            )
        }

        if (anchor === cropSelector) {
            cropPopup?.dismiss()
            cropPopup = popup
        } else {
            varietyPopup?.dismiss()
            varietyPopup = popup
        }

        popup.showAsDropDown(anchor, 0, 4, Gravity.START)
    }

    private fun setupButtons() {
        btnView.setOnClickListener {
            if (selectedCrop == null && selectedVariety == null) {
                Toast.makeText(
                    this,
                    "Please Select Any Crop and Variety",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                viewModel.filterStock(selectedCrop, selectedVariety)
            }
        }

        btnClear.setOnClickListener {
            selectedCrop = null
            selectedVariety = null

            cropSelector.text = "Select Crop"
            varietySelector.text = "Select Variety"

            viewModel.loadStockVarieties(null)
            viewModel.clearStockFilters()
        }
    }

    private fun setHeaderDetails() {
        val session = SessionManager(applicationContext)

        findViewById<TextView>(R.id.txt_title).text = "Stock"
        findViewById<TextView>(R.id.sub_tittle).text =
            "(Ref ID Selection)"

        findViewById<TextView>(R.id.txt_org_code).text =
            session.getOrganizationCode().orEmpty()

        findViewById<TextView>(R.id.txt_date).text =
            SimpleDateFormat(
                "dd-MM-yyyy",
                Locale.getDefault()
            ).format(Date())
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.stockCrops.collect { crops ->
                        val previousSelection = selectedCrop

                        cropItems.clear()
                        cropItems.add("Select Crop")
                        cropItems.addAll(crops)

                        if (previousSelection in crops) {
                            selectedCrop = previousSelection
                        } else {
                            selectedCrop = null
                            cropSelector.text = "Select Crop"
                        }

                        if (cropSelector.text.isNullOrBlank()) {
                            cropSelector.text = "Select Crop"
                        }
                    }
                }

                launch {
                    viewModel.stockVarieties.collect { varieties ->
                        val previousSelection = selectedVariety

                        varietyItems.clear()
                        varietyItems.add("Select Variety")
                        varietyItems.addAll(varieties)

                        if (previousSelection in varieties) {
                            selectedVariety = previousSelection
                            varietySelector.text = previousSelection
                        } else {
                            selectedVariety = null
                            varietySelector.text = "Select Variety"
                        }
                    }
                }

                launch {
                    viewModel.stockList.collect { stock ->
                        val gradeSummary = stock
                            .groupBy { item ->
                                item.buyeR_GRADE
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "UnClassified"
                            }
                            .map { (grade, records) ->
                                StockGradeSummary(
                                    grade = grade,
                                    baleCount = records.size,
                                    quantity = records.sumOf {
                                        it.curR_WT ?: 0.0
                                    }
                                )
                            }
                            .sortedBy { it.grade }

                        stockSummaryAdapter.submitList(gradeSummary)

                        txtTotalBales.text = stock.size.toString()

                        val totalQuantity = stock.sumOf {
                            it.curR_WT ?: 0.0
                        }

                        txtTotalQuantity.text = String.format(
                            Locale.getDefault(),
                            "%.2f",
                            totalQuantity
                        )

                        txtNoData.visibility =
                            if (stock.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.stockError.collect { error ->
                        if (!error.isNullOrBlank()) {
                            Toast.makeText(
                                this@Stock,
                                error,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}
