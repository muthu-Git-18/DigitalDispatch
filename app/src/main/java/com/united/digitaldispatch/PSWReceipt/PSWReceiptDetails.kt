package com.united.digitaldispatch.PSWReceipt

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.R

class PSWReceiptDetails : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswreceipt_details)

        val btnView = findViewById<AppCompatButton>(R.id.btn_view)
        val btnSummary = findViewById<AppCompatButton>(R.id.btn_summary)
        val btnBack = findViewById<ImageView>(R.id.btn_back)

        btnView.setOnClickListener {
            val intent = Intent(
                this@PSWReceiptDetails,
                PSWReceiptView::class.java
            )
            startActivity(intent)
        }

        btnSummary.setOnClickListener {
            val intent = Intent(
                this@PSWReceiptDetails,
                PSWReceiptSummary::class.java
            )
            startActivity(intent)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}