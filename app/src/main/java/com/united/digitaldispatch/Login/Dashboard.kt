package com.united.digitaldispatch.Login

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.united.digitaldispatch.Dispatch.Dispatch
import com.united.digitaldispatch.GTDDispatch.GtdDispatch
import com.united.digitaldispatch.PSWDispatch.PswDispatch
import com.united.digitaldispatch.PSWReceipt.PswReceipt
import com.united.digitaldispatch.R
import com.united.digitaldispatch.Receipt.Receipt

class Dashboard : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Dispatch
        val dispatch = findViewById<LinearLayout>(
            R.id.cardDispatch
        )

        // Receipt
        val receipt = findViewById<LinearLayout>(
            R.id.cardReceipt
        )

        // PSW Dispatch
        val pswdispatch = findViewById<LinearLayout>(
            R.id.cardPswdispatch
        )

        // PSW Receipt
        val pswreceipt = findViewById<LinearLayout>(
            R.id.cardPswreceipt
        )

        // GTD Dispatch
        val gtdispatch = findViewById<LinearLayout>(
            R.id.cardGtdispatch
        )


        // -----------------------------
        // Dispatch
        // -----------------------------

        dispatch.setOnClickListener {

            val intent = Intent(
                this@Dashboard,
                Dispatch::class.java
            )

            startActivity(intent)
        }


        // -----------------------------
        // Receipt
        // -----------------------------

        receipt.setOnClickListener {

            val intent = Intent(
                this@Dashboard,
                Receipt::class.java
            )

            startActivity(intent)
        }


        // -----------------------------
        // PSW Dispatch
        // -----------------------------

        pswdispatch.setOnClickListener {

            val intent = Intent(
                this@Dashboard,
                PswDispatch::class.java
            )

            startActivity(intent)
        }


        // -----------------------------
        // PSW Receipt
        // -----------------------------

        pswreceipt.setOnClickListener {

            val intent = Intent(
                this@Dashboard,
                PswReceipt::class.java
            )

            startActivity(intent)
        }


        // -----------------------------
        // GTD Dispatch
        // -----------------------------

        gtdispatch.setOnClickListener {

            val intent = Intent(
                this@Dashboard,
                GtdDispatch::class.java
            )

            startActivity(intent)
        }
    }
}