package com.united.digitaldispatch.GTDispatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.Dispatch.CreateHeaderDispatch
import com.united.digitaldispatch.Dispatch.DispatchDetails
import com.united.digitaldispatch.R

class GtdDispatch : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gtddispatch)

        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val btnCreateHeader = findViewById<AppCompatButton>(R.id.btn_createHeader)
        val btnProceed = findViewById<AppCompatButton>(R.id.btn_proceed)

        btnBack.setOnClickListener {
            finish()
        }

        btnProceed.setOnClickListener {
            val intent = Intent(this@GtdDispatch, GTDispatchDetails::class.java)
            startActivity(intent)
        }

        btnCreateHeader.setOnClickListener {
            val intent = Intent(this@GtdDispatch, CreateHeaderGTDispatch::class.java)
            startActivity(intent)
        }
    }
}