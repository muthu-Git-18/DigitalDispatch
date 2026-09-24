package com.united.digitaldispatch.Login

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
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
 *
 * Startup sequence on this screen:
 *   1. A one-time "Hello TeamLiftss..... / Items And Stocks Are
 *      Updating....Please Wait.." popup appears bottom-left.
 *   2. After the second message, the popup (lorry + bubble) drives off
 *      to the right and disappears, like the truck pulling away.
 *   3. Right after it's gone, a non-cancelable "Items And Stock Loading"
 *      dialog appears with a flipping hourglass.
 *   4. Card taps (Dispatch/Receipt/etc.) do nothing but show a "please
 *      wait" toast until BOTH startup API calls have reported success.
 *   5. Once both have reported in, the dialog dismisses and the cards
 *      become usable.
 *
 * The two startup API calls are NOT wired in yet -- fetchDispatchAndStockData()
 * is commented out below. Call onApiCallCompleted() once from each of your
 * real API's own success callbacks to unlock the screen; until you do that,
 * the loading dialog will stay up indefinitely, by design.
 */
class Dashboard : AppCompatActivity() {

    private lateinit var session: SessionManager
    private val popupHandler = Handler(Looper.getMainLooper())

    private var loadingDialog: AlertDialog? = null
    private var hourglassAnimator: ObjectAnimator? = null

    /** Flips to true only once BOTH startup API calls have completed. */
    private var isDataLoaded = false
    private var apiCallsCompleted = 0
    private val totalApiCallsNeeded = 2

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
            resizeCard(card, tier)

            // Blocked until isDataLoaded flips true (see onApiCallCompleted()).
            card.container.setOnClickListener {
                if (isDataLoaded) {
                    card.onClick()
                } else {
                    Toast.makeText(
                        this,
                        "Please wait, items and stock are still loading...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        showWelcomePopup()
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

    /**
     * One-time popup in the bottom-left corner:
     *   1. Image + speech bubble fade/scale in
     *   2. Bubble shows "Hello TeamLiftss....." for 4s
     *   3. Cross-fades to "Items And Stocks Are Updating....Please Wait.." for 3s
     *   4. The whole popup (lorry + bubble) DRIVES OFF to the right and
     *      fades out -- not a shrink-in-place, an actual exit to the side.
     *   5. Immediately after it's off-screen, the blocking loading dialog
     *      appears (see showLoadingDialogAndFetchData()).
     */
    private fun showWelcomePopup() {
        val popupContainer = findViewById<View>(R.id.popupContainer)
        val tvPopupText = findViewById<TextView>(R.id.tvPopupText)

        val firstMessage = "Hello TeamLiftss!!....."
        val secondMessage = "Items And Stocks Are\nUpdating....Please Wait.."
        val firstMessageDurationMs = 4000L
        val secondMessageDurationMs = 3000L

        // --- 1. Fade + scale in ---
        popupContainer.visibility = View.VISIBLE
        popupContainer.alpha = 0f
        popupContainer.scaleX = 0.7f
        popupContainer.scaleY = 0.7f
        popupContainer.translationX = 0f
        tvPopupText.text = firstMessage
        tvPopupText.alpha = 1f

        popupContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1200)
            .start()

        // --- 2. Cross-fade to the second message ---
        popupHandler.postDelayed({
            tvPopupText.animate()
                .alpha(0f)
                .setDuration(700)
                .withEndAction {
                    tvPopupText.text = secondMessage
                    tvPopupText.animate()
                        .alpha(1f)
                        .setDuration(700)
                        .start()
                }
                .start()
        }, firstMessageDurationMs)

        // --- 3. Drive the whole popup off to the right, then hand off to the dialog ---
        val totalBeforeExit = firstMessageDurationMs + secondMessageDurationMs
        val driveOffDistance = resources.displayMetrics.widthPixels.toFloat()
        popupHandler.postDelayed({
            popupContainer.animate()
                .translationX(driveOffDistance)
                .alpha(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(900)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    popupContainer.visibility = View.GONE
                    showLoadingDialogAndFetchData()
                }
                .start()
        }, totalBeforeExit)
    }

    /**
     * Shows the non-cancelable "Items And Stock Loading" dialog with the
     * flipping hourglass, and kicks off the two startup data calls. The
     * dialog (and the card-tap block in onCreate) stay up until both calls
     * report back via onApiCallCompleted().
     */
    private fun showLoadingDialogAndFetchData() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)
        val imgHourglass = dialogView.findViewById<ImageView>(R.id.imgHourglass)

        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        loadingDialog?.setCanceledOnTouchOutside(false)
        // The dialog window itself has its own default (square, opaque)
        // background behind whatever view you give it -- without this line
        // you see THAT square edge poking out around our rounded card.
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
        // Without an explicit width the window stretches edge-to-edge;
        // this centers it with breathing room on both sides instead.
        loadingDialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.82).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        startHourglassFlip(imgHourglass)
        //fetchDispatchAndStockData()
    }

    /**
     * PLACEHOLDER for your two real startup API calls.
     *
     * TODO: replace the two postDelayed{} blocks below with your actual
     * network calls (Retrofit/coroutines/etc. -- whatever this project
     * uses elsewhere). Call onApiCallCompleted() exactly once from EACH
     * API's own success callback:
     *
     *   dispatchApi.getStock { result ->
     *       // handle result...
     *       onApiCallCompleted()
     *   }
     *
     *   stockApi.getItems { result ->
     *       // handle result...
     *       onApiCallCompleted()
     *   }
     *
     * Do not call onApiCallCompleted() more than once per API, and don't
     * call it on failure unless you want the screen to unlock anyway --
     * for a failed call you'll more likely want to dismiss the dialog,
     * show an error, and offer a retry instead.
     */
    /*  private fun fetchDispatchAndStockData() {
          // Simulated "API #1" -- remove once the real call is wired in.
          popupHandler.postDelayed({
              onApiCallCompleted()
          }, 2500)

          // Simulated "API #2" -- remove once the real call is wired in.
          popupHandler.postDelayed({
              onApiCallCompleted()
          }, 3500)
      }*/

    /**
     * Call this once per startup API call, from that API's own success
     * callback. Once both have reported in, the loading dialog is
     * dismissed and the dashboard cards unlock.
     */
    private fun onApiCallCompleted() {
        apiCallsCompleted++
        if (apiCallsCompleted >= totalApiCallsNeeded) {
            hourglassAnimator?.cancel()
            loadingDialog?.dismiss()
            isDataLoaded = true
        }
    }

    /** Continuously flips the hourglass image 180 degrees, pauses, flips back, repeats. */
    private fun startHourglassFlip(imageView: ImageView) {
        val kf0 = Keyframe.ofFloat(0f, 0f)
        val kf1 = Keyframe.ofFloat(0.3f, 180f)   // flip down
        val kf2 = Keyframe.ofFloat(0.55f, 180f)  // hold
        val kf3 = Keyframe.ofFloat(0.85f, 360f)  // flip back
        val kf4 = Keyframe.ofFloat(1f, 360f)     // hold
        val rotationHolder = PropertyValuesHolder.ofKeyframe("rotation", kf0, kf1, kf2, kf3, kf4)

        hourglassAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, rotationHolder).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any pending popup/API-simulation callbacks and stop the
        // animation / dismiss the dialog so nothing touches views or leaks
        // a window after the Activity is gone.
        popupHandler.removeCallbacksAndMessages(null)
        hourglassAnimator?.cancel()
        loadingDialog?.dismiss()
    }

    /*
     * ------------------------------------------------------------------
     * OPTIONAL: show the welcome popup only on the very first app launch
     * ever, instead of every time the dashboard opens. Replace the
     * showWelcomePopup() call in onCreate() with
     * maybeShowWelcomePopupOnce() below. The loading dialog / API gate
     * is unaffected either way -- it always runs, since it's gating real
     * data, not just a greeting.
     * ------------------------------------------------------------------
     *
     * private fun maybeShowWelcomePopupOnce() {
     *     val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
     *     val alreadyShown = prefs.getBoolean("welcome_popup_shown", false)
     *     if (!alreadyShown) {
     *         showWelcomePopup()
     *         prefs.edit().putBoolean("welcome_popup_shown", true).apply()
     *     } else {
     *         showLoadingDialogAndFetchData()
     *     }
     * }
     */
}