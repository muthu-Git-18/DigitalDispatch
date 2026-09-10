package com.united.digitaldispatch.PSWReceipt

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.united.digitaldispatch.R

class PSWReceiptSummary : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswreceipt_summary)

        val btnBack = findViewById<ImageView>(R.id.btn_back);
        btnBack.setOnClickListener{
            finish();
        }
    }
}