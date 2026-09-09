package com.united.digitaldispatch.Dispatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.R

class Dispatch : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dispatch)

        val btnBack = findViewById<ImageView>(R.id.btn_back)
        val btnCreateHeader = findViewById<AppCompatButton>(R.id.btn_createHeader)

        btnBack.setOnClickListener {
            finish()
        }



        btnCreateHeader.setOnClickListener {
            val intent = Intent(this@Dispatch, CreateHeaderDispatch::class.java)
            startActivity(intent)
        }
    }
}