package com.united.digitaldispatch.Receipt

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.united.digitaldispatch.R

class ReceiptDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_details)

        val btnView = findViewById<AppCompatButton>(R.id.btn_view);

        val btnSummary = findViewById<AppCompatButton>(R.id.btn_summary);

        btnView.setOnClickListener{
            val intent = Intent(this@ReceiptDetails, ReceiptView::class.java)
            startActivity(intent)
        }

        btnSummary.setOnClickListener{
            val intent = Intent(this@ReceiptDetails, ReceiptSummary::class.java)
            startActivity(intent)
        }

    }
}