package com.united.digitaldispatch.GTDispatch

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.united.digitaldispatch.R

class GTDispatchView : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gtdispatch_view)

        val btnBack = findViewById<ImageView>(R.id.btn_back);

        btnBack.setOnClickListener{
            finish();
        }
    }
}