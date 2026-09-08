package com.united.digitaldispatch.Login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.united.digitaldispatch.R

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {

            val intent = Intent(
                this@LoginActivity,
                Dashboard::class.java
            )

            startActivity(intent)

            // Prevent going back to Login screen
            finish()
        }
    }
}