package com.united.digitaldispatch.Login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.united.digitaldispatch.Dispatch.Dispatch
import com.united.digitaldispatch.GTDispatch.GtdDispatch
import com.united.digitaldispatch.PSWDispatch.PswDispatch
import com.united.digitaldispatch.PSWReceipt.PswReceipt
import com.united.digitaldispatch.R
import com.united.digitaldispatch.Receipt.Receipt
import com.united.digitaldispatch.data.local.DashboardScreen
import com.united.digitaldispatch.data.local.ModuleScreens
import com.united.digitaldispatch.utils.SessionManager

/**
 * Shows only the cards that belong to the module the user logged into
 * (see [ModuleScreens] for the TAP/PPD/GLT/WH -> screens mapping you gave
 * us). If session data is somehow missing, we bounce back to Login rather
 * than showing every module's screens.
 */
class Dashboard : AppCompatActivity() {

    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        session = SessionManager(applicationContext)
        val moduleType = session.getModuleType()

        if (moduleType == null || !session.isLoggedIn()) {
            Toast.makeText(this, "Session expired, please log in again", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        findViewById<TextView>(R.id.tvDashboard).text = "$moduleType Dashboard"

        val allowedScreens = ModuleScreens.allowedScreens(moduleType)

        val cardDispatch = findViewById<LinearLayout>(R.id.cardDispatch)
        val cardReceipt = findViewById<LinearLayout>(R.id.cardReceipt)
        val cardPswDispatch = findViewById<LinearLayout>(R.id.cardPswdispatch)
        val cardPswReceipt = findViewById<LinearLayout>(R.id.cardPswreceipt)
        val cardGtdDispatch = findViewById<LinearLayout>(R.id.cardGtdispatch)

        applyVisibility(cardDispatch, DashboardScreen.DISPATCH, allowedScreens) {
            startActivity(Intent(this, Dispatch::class.java))
        }
        applyVisibility(cardReceipt, DashboardScreen.RECEIPT, allowedScreens) {
            startActivity(Intent(this, Receipt::class.java))
        }
        applyVisibility(cardPswDispatch, DashboardScreen.PSW_DISPATCH, allowedScreens) {
            startActivity(Intent(this, PswDispatch::class.java))
        }
        applyVisibility(cardPswReceipt, DashboardScreen.PSW_RECEIPT, allowedScreens) {
            startActivity(Intent(this, PswReceipt::class.java))
        }
        applyVisibility(cardGtdDispatch, DashboardScreen.GTD_DISPATCH, allowedScreens) {
            startActivity(Intent(this, GtdDispatch::class.java))
        }
    }

    private fun applyVisibility(
        card: LinearLayout,
        screen: DashboardScreen,
        allowedScreens: Set<DashboardScreen>,
        onClick: () -> Unit
    ) {
        if (screen in allowedScreens) {
            card.visibility = View.VISIBLE
            card.setOnClickListener { onClick() }
        } else {
            card.visibility = View.GONE
        }
    }
}
