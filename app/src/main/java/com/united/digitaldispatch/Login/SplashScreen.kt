package com.united.digitaldispatch.Login

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.united.digitaldispatch.Apiservice.network.ApiClient
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Apiservice.viewmodel.MasterDataSyncState
import com.united.digitaldispatch.Apiservice.viewmodel.MasterDataViewModel
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.utils.DotWaveLoadingView
import kotlinx.coroutines.launch

class SplashScreen : AppCompatActivity() {

    private lateinit var imgLogo: ImageView
    private lateinit var dotWaveLoading: DotWaveLoadingView
    private lateinit var txtLoading: TextView

    private val viewModel: MasterDataViewModel by viewModels {
        object : ViewModelProvider.Factory {

            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {

                val repository = MasterDataRepository(
                    ApiClient.instance,
                    AppDatabase.getInstance(applicationContext)
                )

                return MasterDataViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash_screen)

        imgLogo = findViewById(R.id.imgLogo)
        dotWaveLoading = findViewById(R.id.dotWaveLoading)
        txtLoading = findViewById(R.id.txtLoading)

        startLogoAnimation()
        // dotWaveLoading animates itself automatically once attached to the
        // window -- no start()/stop() calls needed, unlike the hourglass.

        observeMasterData()

        // Start API call
        viewModel.syncMasterData()
    }

    private fun observeMasterData() {

        lifecycleScope.launch {

            viewModel.syncState.collect { state ->

                when (state) {

                    is MasterDataSyncState.Idle -> {
                        dotWaveLoading.visibility = View.VISIBLE
                        txtLoading.text = "Preparing..."
                    }

                    is MasterDataSyncState.Loading -> {
                        dotWaveLoading.visibility = View.VISIBLE
                        txtLoading.text = "Syncing master data..."
                    }

                    is MasterDataSyncState.Success -> {

                        dotWaveLoading.visibility = View.GONE
                        txtLoading.text = "Sync completed"

                        openLoginScreen()
                    }

                    is MasterDataSyncState.Error -> {

                        //dotWaveLoading.visibility = View.GONE
                        txtLoading.text = "Unable to sync master data"

                        // For now, don't open LoginActivity
                        // because login depends on the master data.
                    }
                }
            }
        }
    }

    private fun openLoginScreen() {

        val intent = Intent(
            this@SplashScreen,
            LoginActivity::class.java
        )

        startActivity(intent)
        finish()
    }

    private fun startLogoAnimation() {

        // Gives perspective for 3D turning effect
        imgLogo.cameraDistance =
            12000 * resources.displayMetrics.density

        val animator = ObjectAnimator.ofFloat(
            imgLogo,
            "rotationY",
            0f, 8f, 0f, -8f, 0f
        )

        animator.duration = 2500

        animator.repeatCount = ObjectAnimator.INFINITE

        animator.repeatMode = ObjectAnimator.RESTART

        animator.interpolator =
            AccelerateDecelerateInterpolator()

        animator.start()
    }
}