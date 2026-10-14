package com.united.digitaldispatch.Stock

import android.os.Bundle
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.united.digitaldispatch.R

class Stock : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock)

        val cropSpinner = findViewById<Spinner>(R.id.spinner_crop_year)
        val varietySpinner = findViewById<Spinner>(R.id.spinner_variety_year)
    }
}