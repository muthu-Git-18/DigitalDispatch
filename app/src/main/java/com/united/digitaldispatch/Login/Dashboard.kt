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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Dispatch.Dispatch
import com.united.digitaldispatch.GTDispatch.GtdDispatch
import com.united.digitaldispatch.PSWDispatch.PswDispatch
import com.united.digitaldispatch.PSWReceipt.PswReceipt
import com.united.digitaldispatch.R
import com.united.digitaldispatch.Receipt.Receipt
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.DashboardScreen
import com.united.digitaldispatch.data.local.ModuleScreens
import com.united.digitaldispatch.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *   1. A one-time "Hello <name>!!..... / Items And Stocks Are
 *      Updating....Please Wait.." popup appears bottom-left, and the
 *      Stock API starts syncing in the background at the same time.
 *   2. After the second message, the popup (lorry + bubble) drives off
 *      to the right and disappears, like the truck pulling away.
 *   3. Right after it's gone, a non-cancelable "Items And Stock Loading"
 *      dialog appears with a flipping hourglass AND a progress bar that
 *      tracks the Stock sync's real download progress (percent is based
 *      on records-downloaded / totalRecords from the API itself).
 *   4. If the sync already finished during step 1-2 (common, since it
 *      starts early and most syncs are a single page), the dialog is
 *      skipped entirely instead of popping up and never dismissing.
 *   5. Card taps do nothing but show a "please wait" toast until the
 *      Stock API has reported success.
 *   6. Once it reports in, a toast shows the total records synced, the
 *      dialog (if shown) dismisses, and the cards become usable.
 */
class Dashboard : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var repository: MasterDataRepository

    private val popupHandler = Handler(Looper.getMainLooper())

    private var loadingDialog: AlertDialog? = null
    private var hourglassAnimator: ObjectAnimator? = null
    private var loadingProgressBar: ProgressBar? = null
    private var tvLoadingPercent: TextView? = null

    /** Latest known sync progress, kept even while no dialog is showing yet. */
    private var lastProgressPercent = 0
    private var lastProgressCurrent = 0
    private var lastProgressTotal = 0

    /** Flips to true only once the startup Stock API has completed. */
    private var isDataLoaded = false
    private var apiCallsCompleted = 0
    private val totalApiCallsNeeded = 1

    private lateinit var tvDashboardName : TextView

    private lateinit var orgCode : String

    private lateinit var moduleType : String

    private lateinit var userName : String

    private var isSyncing = false
    private lateinit var syncButtonContainer: FrameLayout
    private lateinit var btnSync: ImageView
    private var syncIconAnimator: ObjectAnimator? = null



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
    private data class SizeTier(
        val cardSizeDp: Int,
        val iconSizeDp: Int,
        val labelSizeSp: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dashboard)

        session = SessionManager(applicationContext)

        inits()

        setContentData()

    }

    private fun inits(){

        tvDashboardName = findViewById(R.id.tvDashboard)
        syncButtonContainer = findViewById(R.id.syncButtonContainer)
        btnSync = findViewById(R.id.btnSync)

        syncButtonContainer.isEnabled = false
        syncButtonContainer.alpha = 0.5f

        syncButtonContainer.setOnClickListener {
            onSyncButtonClicked()
        }
    }


    private fun setContentData(){
        repository = MasterDataRepository(
            ApiClient.instance,
            AppDatabase.getInstance(applicationContext)
        )

        val module = session.getModuleType()

        if (module.isNullOrBlank() || !session.isLoggedIn()) {

            Toast.makeText(
                this,
                "Session expired, please log in again",
                Toast.LENGTH_SHORT
            ).show()

            startActivity(
                Intent(
                    this,
                    LoginActivity::class.java
                )
            )

            finish()
            return
        }

        moduleType = module
        orgCode= session.getOrganizationCode().toString()
        userName = session.getUserName().orEmpty()

        tvDashboardName.text = "$moduleType Dashboard"


        val allowedScreens = ModuleScreens.allowedScreens(moduleType)

        val allCards = listOf(
            CardViews(
                container = findViewById(R.id.cardDispatch),
                iconFrame = findViewById(R.id.frameDispatchIcon),
                icon = findViewById(R.id.imgDispatchIcon),
                label = findViewById(R.id.tvDispatchLabel),
                screen = DashboardScreen.DISPATCH
            ) {
                startActivity(
                    Intent(
                        this,
                        Dispatch::class.java
                    )
                )
            },

            CardViews(
                container = findViewById(R.id.cardReceipt),
                iconFrame = findViewById(R.id.frameReceiptIcon),
                icon = findViewById(R.id.imgReceiptIcon),
                label = findViewById(R.id.tvReceiptLabel),
                screen = DashboardScreen.RECEIPT
            ) {
                startActivity(
                    Intent(
                        this,
                        Receipt::class.java
                    )
                )
            },

            CardViews(
                container = findViewById(R.id.cardPswdispatch),
                iconFrame = findViewById(R.id.framePswdispatchIcon),
                icon = findViewById(R.id.imgPswdispatchIcon),
                label = findViewById(R.id.tvPswdispatchLabel),
                screen = DashboardScreen.PSW_DISPATCH
            ) {
                startActivity(
                    Intent(
                        this,
                        PswDispatch::class.java
                    )
                )
            },

            CardViews(
                container = findViewById(R.id.cardPswreceipt),
                iconFrame = findViewById(R.id.framePswreceiptIcon),
                icon = findViewById(R.id.imgPswreceiptIcon),
                label = findViewById(R.id.tvPswreceiptLabel),
                screen = DashboardScreen.PSW_RECEIPT
            ) {
                startActivity(
                    Intent(
                        this,
                        PswReceipt::class.java
                    )
                )
            },

            CardViews(
                container = findViewById(R.id.cardGtdispatch),
                iconFrame = findViewById(R.id.frameGtdispatchIcon),
                icon = findViewById(R.id.imgGtdispatchIcon),
                label = findViewById(R.id.tvGtdispatchLabel),
                screen = DashboardScreen.GTD_DISPATCH
            ) {
                startActivity(
                    Intent(
                        this,
                        GtdDispatch::class.java
                    )
                )
            }
        )

        val visibleCards = allCards.filter {
            it.screen in allowedScreens
        }

        val hiddenCards = allCards.filter {
            it.screen !in allowedScreens
        }

        hiddenCards.forEach {
            it.container.visibility = View.GONE
        }

        val tier = sizeTierFor(visibleCards.size)

        visibleCards.forEach { card ->

            card.container.visibility = View.VISIBLE

            resizeCard(
                card,
                tier
            )

            // Blocked until isDataLoaded flips true.
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
     * Kicks off the Stock API in the background. Progress updates arrive
     * via onProgress on whatever thread syncStock() is currently running
     * on (Dispatchers.IO here), so they're hopped back to the main thread
     * with runOnUiThread before touching any views.
     *
     * On success: records the final count, updates the progress UI to a
     * clean 100%, shows a "synced successfully" toast with the total
     * record count, and calls onApiCallCompleted() to unlock the screen.
     *
     */

    private fun fetchTruckMasterData() {

        if (orgCode.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Organization code is missing",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {

            val result = withContext(Dispatchers.IO) {
                repository.syncTruckMaster(orgCode)
            }

            if (result.isSuccess) {

                android.util.Log.d(
                    "Dashboard",
                    "Truck master sync completed successfully"
                )

                fetchStockData()

            } else {

                android.util.Log.e(
                    "Dashboard",
                    "Truck master sync failed",
                    result.exceptionOrNull()
                )

                Toast.makeText(
                    this@Dashboard,
                    "Truck master sync failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    private fun fetchStockData() {

        val orgnType = session.getModuleType()
        val orgnCode = session.getOrganizationCode()

        if (orgnType.isNullOrBlank() || orgnCode.isNullOrBlank()) {

            Toast.makeText(
                this,
                "Session organization details are missing",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        lifecycleScope.launch {

            val result = withContext(Dispatchers.IO) {

                repository.syncStock(
                    orgnType = orgnType,
                    orgnCode = orgnCode
                ) { percent, current, total ->

                    runOnUiThread {
                        updateLoadingProgress(percent, current, total)
                    }
                }
            }

            if (result.isSuccess) {

                val totalSynced = result.getOrDefault(0)

                android.util.Log.d(
                    "Dashboard",
                    "Stock sync completed successfully: $totalSynced records"
                )

                updateLoadingProgress(100, totalSynced, totalSynced)

                Toast.makeText(
                    this@Dashboard,
                    "Stock Updated Successfully: $totalSynced records",
                    Toast.LENGTH_LONG
                ).show()

                onApiCallCompleted()

            } else {

                android.util.Log.e(
                    "Dashboard",
                    "Stock sync failed",
                    result.exceptionOrNull()
                )

                Toast.makeText(
                    this@Dashboard,
                    "Stock sync failed",
                    Toast.LENGTH_LONG
                ).show()

                // NOTE: onApiCallCompleted() is intentionally NOT called
                // here, so the screen stays locked and the dialog (if
                // shown) stays up on failure rather than silently letting
                // the user in with stale/missing stock data. Right now
                // that means it stays locked indefinitely on failure --
                // worth adding a dismiss + retry action here later.
            }
        }
    }


    /**
     * Manual re-sync via the floating sync button. Unlike the first-launch
     * flow in showWelcomePopup(), this skips the welcome popup entirely and
     * goes straight to the blocking loading dialog + Stock API call, while
     * the sync icon itself spins for as long as that dialog stays up.
     */
    private fun onSyncButtonClicked() {

        if (!isDataLoaded || isSyncing) {

            Toast.makeText(
                this,
                "Please wait, sync is already in progress...",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        isSyncing = true
        isDataLoaded = false
        apiCallsCompleted = 0

        // Reset tracked progress so the dialog opens at 0%, not the
        // previous sync's leftover 100%.
        lastProgressPercent = 0
        lastProgressCurrent = 0
        lastProgressTotal = 0

        syncButtonContainer.isEnabled = false
        syncButtonContainer.alpha = 0.5f

        startSyncIconRotation()
        showLoadingDialogAndFetchData()
        fetchStockData()
    }

    private fun startSyncIconRotation() {

        syncIconAnimator?.cancel()

        syncIconAnimator = ObjectAnimator.ofFloat(
            btnSync,
            "rotation",
            0f,
            360f
        ).apply {

            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopSyncIconRotation() {

        syncIconAnimator?.cancel()
        syncIconAnimator = null
        btnSync.rotation = 0f
    }

    /**
     * 1 card -> big, 2 -> medium-large, 3 -> medium, 4+ -> a bit bigger
     * than the original fixed 100dp design. Only sizes that actually occur
     * per your module mapping (1, 2, 4) matter in practice; 3/5+ are just
     * sensible fallbacks in case the mapping changes later.
     */
    private fun sizeTierFor(visibleCount: Int): SizeTier = when (visibleCount) {

        1 -> SizeTier(
            cardSizeDp = 210,
            iconSizeDp = 120,
            labelSizeSp = 18f
        )

        2 -> SizeTier(
            cardSizeDp = 160,
            iconSizeDp = 95,
            labelSizeSp = 14f
        )

        3 -> SizeTier(
            cardSizeDp = 130,
            iconSizeDp = 80,
            labelSizeSp = 12f
        )

        else -> SizeTier(
            cardSizeDp = 115,
            iconSizeDp = 75,
            labelSizeSp = 11f
        )
    }

    private fun resizeCard(
        card: CardViews,
        tier: SizeTier
    ) {

        val cardPx = dp(tier.cardSizeDp)
        val iconPx = dp(tier.iconSizeDp)

        card.container.layoutParams =
            card.container.layoutParams.apply {

                width = cardPx
                height = cardPx
            }

        card.iconFrame.layoutParams =
            card.iconFrame.layoutParams.apply {

                width = iconPx
                height = iconPx
            }

        card.icon.layoutParams =
            card.icon.layoutParams.apply {

                width = iconPx
                height = iconPx
            }

        card.label.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            tier.labelSizeSp
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /**
     * One-time popup in the bottom-left corner:
     *   1. Image + speech bubble fade/scale in
     *   2. Bubble shows "Hello <name>!!....." for 4s
     *   3. Cross-fades to "Items And Stocks Are Updating....Please Wait.."
     *   4. The whole popup drives off to the right and fades out.
     *   5. Immediately after it's off-screen, the blocking loading dialog
     *      appears (unless the sync already finished -- see
     *      showLoadingDialogAndFetchData()).
     */
    private fun showWelcomePopup() {

        // Start Stock API in background immediately
        fetchTruckMasterData()

        val popupContainer =
            findViewById<View>(R.id.popupContainer)

        val tvPopupText =
            findViewById<TextView>(R.id.tvPopupText)

        val firstMessage =
            "Hello $userName!!....."

        val secondMessage =
            "Items And Stocks Are\nUpdating....Please Wait.."

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

        // --- 3. Drive the whole popup off to the right ---

        val totalBeforeExit =
            firstMessageDurationMs + secondMessageDurationMs

        val driveOffDistance =
            resources.displayMetrics.widthPixels.toFloat()

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
     * Shows the non-cancelable loading dialog with the flipping hourglass
     * and a progress bar reflecting the Stock sync's real download
     * progress. If the sync already finished by the time this runs (it
     * starts back in showWelcomePopup(), so on a fast/small sync it can
     * easily beat the popup's ~7s animation), the dialog is skipped
     * entirely instead of popping up with nothing left to ever dismiss it.
     */
    private fun showLoadingDialogAndFetchData() {

        if (isDataLoaded) {
            return
        }

        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_loading,
                null
            )

        val imgHourglass =
            dialogView.findViewById<ImageView>(
                R.id.imgHourglass
            )

        loadingProgressBar = dialogView.findViewById(R.id.progressLoading)
        tvLoadingPercent = dialogView.findViewById(R.id.tvLoadingPercent)

        // Reflect whatever progress already happened in the background
        // while the welcome popup was playing, instead of restarting at 0%.
        loadingProgressBar?.progress = lastProgressPercent
        tvLoadingPercent?.text = "$lastProgressPercent%"

        loadingDialog =
            AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()

        loadingDialog?.setCanceledOnTouchOutside(false)

        loadingDialog?.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        loadingDialog?.show()

        loadingDialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.82).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        startHourglassFlip(imgHourglass)


    }

    /**
     * Updates the progress bar/percentage in the loading dialog if it's
     * currently showing, and always remembers the latest values so a
     * dialog created LATER (see showLoadingDialogAndFetchData()) can pick
     * up wherever the sync actually is instead of starting over at 0%.
     */
    private fun updateLoadingProgress(percent: Int, current: Int, total: Int) {

        lastProgressPercent = percent
        lastProgressCurrent = current
        lastProgressTotal = total

        loadingProgressBar?.progress = percent
        tvLoadingPercent?.text = "$percent%"
    }

    /**
     * Called once when the Stock API completes successfully.
     * Once the required API count is reached, the loading dialog
     * is dismissed and the dashboard cards become usable.
     */
    private fun onApiCallCompleted() {

        apiCallsCompleted++

        if (apiCallsCompleted >= totalApiCallsNeeded) {

            isDataLoaded = true
            isSyncing = false

            hourglassAnimator?.cancel()
            stopSyncIconRotation()

            syncButtonContainer.isEnabled = true
            syncButtonContainer.alpha = 1f

            loadingDialog?.dismiss()
        }
    }

    /** Continuously flips the hourglass image 180 degrees, pauses, flips back, repeats. */
    private fun startHourglassFlip(imageView: ImageView) {

        val kf0 = Keyframe.ofFloat(
            0f,
            0f
        )

        val kf1 = Keyframe.ofFloat(
            0.3f,
            180f
        )

        val kf2 = Keyframe.ofFloat(
            0.55f,
            180f
        )

        val kf3 = Keyframe.ofFloat(
            0.85f,
            360f
        )

        val kf4 = Keyframe.ofFloat(
            1f,
            360f
        )

        val rotationHolder =
            PropertyValuesHolder.ofKeyframe(
                "rotation",
                kf0,
                kf1,
                kf2,
                kf3,
                kf4
            )

        hourglassAnimator =
            ObjectAnimator.ofPropertyValuesHolder(
                imageView,
                rotationHolder
            ).apply {

                duration = 3000

                repeatCount =
                    ObjectAnimator.INFINITE

                interpolator =
                    LinearInterpolator()

                start()
            }
    }

    override fun onDestroy() {

        super.onDestroy()

        popupHandler.removeCallbacksAndMessages(null)

        hourglassAnimator?.cancel()
        syncIconAnimator?.cancel()

        loadingDialog?.dismiss()
    }
}