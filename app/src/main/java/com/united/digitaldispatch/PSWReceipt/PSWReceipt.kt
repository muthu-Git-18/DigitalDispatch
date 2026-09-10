package com.united.digitaldispatch.PSWReceipt

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.R
import com.united.digitaldispatch.Receipt.ReceiptDetails

class PswReceipt : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswreceipt)

        val btnproceed = findViewById<AppCompatButton>(R.id.btn_proceed);
        val btnBack = findViewById<ImageView>(R.id.btn_back);

        btnBack.setOnClickListener{
            finish();
        }

        btnproceed.setOnClickListener{
            val intent = Intent(this@PswReceipt, PSWReceiptDetails::class.java)
            startActivity(intent)
        }
    }
}