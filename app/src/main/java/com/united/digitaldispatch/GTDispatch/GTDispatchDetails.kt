package com.united.digitaldispatch.GTDispatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.united.digitaldispatch.Dispatch.DispatchSummary
import com.united.digitaldispatch.Dispatch.DispatchView
import com.united.digitaldispatch.R

class GTDispatchDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gtdispatch_details)

        val btnGradewise = findViewById<AppCompatButton>(R.id.btn_gradewise)
        val btnView = findViewById<AppCompatButton>(R.id.btn_view)
        val btnBack = findViewById<ImageView>(R.id.btn_back);

        btnBack.setOnClickListener{
            finish();
        }

        btnGradewise.setOnClickListener {
            val intent = Intent(this@GTDispatchDetails, GTDispatchSummary::class.java)
            startActivity(intent)
        }


        btnView.setOnClickListener {
            val intent = Intent(this@GTDispatchDetails, GTDispatchView::class.java)
            startActivity(intent)
        }
    }
}