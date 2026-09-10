package com.united.digitaldispatch.Dispatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.united.digitaldispatch.R

class DispatchDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispatch_details)

        val btnGradewise = findViewById<AppCompatButton>(R.id.btn_gradewise)
        val btnView = findViewById<AppCompatButton>(R.id.btn_view)

        btnGradewise.setOnClickListener {
            val intent = Intent(this@DispatchDetails, DispatchSummary::class.java)
            startActivity(intent)
        }


        btnView.setOnClickListener {
            val intent = Intent(this@DispatchDetails, DispatchView::class.java)
            startActivity(intent)
        }
    }
}