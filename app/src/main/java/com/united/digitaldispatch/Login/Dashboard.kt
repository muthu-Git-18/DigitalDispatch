package com.united.digitaldispatch.Login

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
 * (see [ModuleScreens] for the TAP/PPD/GLT/WH -> screens mapping). On top
 * of that, cards are SIZED based on how many are visible -- 1 card (TAP)
 * gets shown big, 2 cards (PPD) medium-large, 4 cards (GLT/WH) a bit
 * bigger than the original design -- so the screen doesn't look sparse
 * with lots of empty space when a module only has 1-2 screens.
 *
 * If session data is somehow missing, we bounce back to Login rather
 * than showing every module's screens.
 */
class Dashboard : AppCompatActivity() {

    private lateinit var session: SessionManager

    /** Holds references to one card's outer container + the 3 inner views that get resized. */
    private data class CardViews(
        val container: LinearLayout,
        val iconFrame: FrameLayout,
        val icon: ImageView,
        val label: TextView,
        val screen: DashboardScreen,
        val onClick: () -> Unit
    )

    /** cardSizeDp, iconSizeDp, labelSizeSp -- tuned per visible-card-count. */
    private data class SizeTier(val cardSizeDp: Int, val iconSizeDp: Int, val labelSizeSp: Float)

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

        val allCards = listOf(
            CardViews(
                container = findViewById(R.id.cardDispatch),
                iconFrame = findViewById(R.id.frameDispatchIcon),
                icon = findViewById(R.id.imgDispatchIcon),
                label = findViewById(R.id.tvDispatchLabel),
                screen = DashboardScreen.DISPATCH
            ) { startActivity(Intent(this, Dispatch::class.java)) },
            CardViews(
                container = findViewById(R.id.cardReceipt),
                iconFrame = findViewById(R.id.frameReceiptIcon),
                icon = findViewById(R.id.imgReceiptIcon),
                label = findViewById(R.id.tvReceiptLabel),
                screen = DashboardScreen.RECEIPT
            ) { startActivity(Intent(this, Receipt::class.java)) },
            CardViews(
                container = findViewById(R.id.cardPswdispatch),
                iconFrame = findViewById(R.id.framePswdispatchIcon),
                icon = findViewById(R.id.imgPswdispatchIcon),
                label = findViewById(R.id.tvPswdispatchLabel),
                screen = DashboardScreen.PSW_DISPATCH
            ) { startActivity(Intent(this, PswDispatch::class.java)) },
            CardViews(
                container = findViewById(R.id.cardPswreceipt),
                iconFrame = findViewById(R.id.framePswreceiptIcon),
                icon = findViewById(R.id.imgPswreceiptIcon),
                label = findViewById(R.id.tvPswreceiptLabel),
                screen = DashboardScreen.PSW_RECEIPT
            ) { startActivity(Intent(this, PswReceipt::class.java)) },
            CardViews(
                container = findViewById(R.id.cardGtdispatch),
                iconFrame = findViewById(R.id.frameGtdispatchIcon),
                icon = findViewById(R.id.imgGtdispatchIcon),
                label = findViewById(R.id.tvGtdispatchLabel),
                screen = DashboardScreen.GTD_DISPATCH
            ) { startActivity(Intent(this, GtdDispatch::class.java)) }
        )

        val visibleCards = allCards.filter { it.screen in allowedScreens }
        val hiddenCards = allCards.filter { it.screen !in allowedScreens }

        hiddenCards.forEach { it.container.visibility = View.GONE }

        val tier = sizeTierFor(visibleCards.size)
        visibleCards.forEach { card ->
            card.container.visibility = View.VISIBLE
            card.container.setOnClickListener { card.onClick() }
            resizeCard(card, tier)
        }
    }

    /**
     * 1 card -> big, 2 -> medium-large, 3 -> medium, 4+ -> a bit bigger
     * than the original fixed 100dp design. Only sizes that actually occur
     * per your module mapping (1, 2, 4) matter in practice; 3/5+ are just
     * sensible fallbacks in case the mapping changes later.
     */
    private fun sizeTierFor(visibleCount: Int): SizeTier = when (visibleCount) {
        1 -> SizeTier(cardSizeDp = 210, iconSizeDp = 120, labelSizeSp = 18f)
        2 -> SizeTier(cardSizeDp = 160, iconSizeDp = 95, labelSizeSp = 14f)
        3 -> SizeTier(cardSizeDp = 130, iconSizeDp = 80, labelSizeSp = 12f)
        else -> SizeTier(cardSizeDp = 115, iconSizeDp = 75, labelSizeSp = 11f)
    }

    private fun resizeCard(card: CardViews, tier: SizeTier) {
        val cardPx = dp(tier.cardSizeDp)
        val iconPx = dp(tier.iconSizeDp)

        card.container.layoutParams = card.container.layoutParams.apply {
            width = cardPx
            height = cardPx
        }
        card.iconFrame.layoutParams = card.iconFrame.layoutParams.apply {
            width = iconPx
            height = iconPx
        }
        card.icon.layoutParams = card.icon.layoutParams.apply {
            width = iconPx
            height = iconPx
        }
        card.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, tier.labelSizeSp)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}