package com.united.digitaldispatch.Receipt

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.united.digitaldispatch.R

class ReceiptHeaderSelection : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)

        val btnBack = findViewById<ImageView>(R.id.btn_back);

        btnBack.setOnClickListener{
            finish();
        }
    }
}