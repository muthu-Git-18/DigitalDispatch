package com.united.digitaldispatch.PSWDispatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.Dispatch.DispatchSummary
import com.united.digitaldispatch.Dispatch.DispatchView
import com.united.digitaldispatch.R

class PSWDispatchDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pswdispatch_details)

        val btnBack = findViewById<ImageView>(R.id.btn_back);
        val btnGradewise = findViewById<AppCompatButton>(R.id.btn_gradewise)
        val btnView = findViewById<AppCompatButton>(R.id.btn_view)

        btnBack.setOnClickListener{
            finish();
        }

        btnGradewise.setOnClickListener {
            val intent = Intent(this@PSWDispatchDetails, PSWDispatchSummary::class.java)
            startActivity(intent)
        }


        btnView.setOnClickListener {
            val intent = Intent(this@PSWDispatchDetails, PSWDispatchView::class.java)
            startActivity(intent)
        }
    }
}