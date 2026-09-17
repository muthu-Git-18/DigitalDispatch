package com.united.digitaldispatch.PSWReceipt

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.R

class PswReceipt : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswreceipt)

        val btnProceed = findViewById<AppCompatButton>(R.id.btn_proceed)
        val btnBack = findViewById<ImageView>(R.id.btn_back)

        btnProceed.setOnClickListener {
            val intent = Intent(
                this@PswReceipt,
                PSWReceiptDetails::class.java
            )
            startActivity(intent)
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}