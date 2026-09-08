package com.united.digitaldispatch.Receipt

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.R

class Receipt : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)

        val btnBack = findViewById<ImageView>(R.id.btn_back);

        val btnproceed = findViewById<AppCompatButton>(R.id.btn_proceed);

        btnBack.setOnClickListener{
            finish();
        }

        btnproceed.setOnClickListener{
            val intent = Intent(this@Receipt, ReceiptDetails::class.java)
            startActivity(intent)
        }
    }
}