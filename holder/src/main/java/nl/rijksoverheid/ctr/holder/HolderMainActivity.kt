/*
 *  Copyright (c) 2021 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *   Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *   SPDX-License-Identifier: EUPL-1.2
 *
 */

package nl.rijksoverheid.ctr.holder

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import nl.rijksoverheid.ctr.appconfig.AppConfigViewModel
import nl.rijksoverheid.ctr.appconfig.AppStatusFragment
import nl.rijksoverheid.ctr.appconfig.models.AppStatus
import nl.rijksoverheid.ctr.design.utils.DialogUtil
import nl.rijksoverheid.ctr.holder.databinding.ActivityMainBinding
import nl.rijksoverheid.ctr.holder.ui.device_rooted.DeviceRootedViewModel
import nl.rijksoverheid.ctr.introduction.IntroductionFragment
import nl.rijksoverheid.ctr.introduction.IntroductionViewModel
import nl.rijksoverheid.ctr.introduction.ui.status.models.IntroductionStatus
import nl.rijksoverheid.ctr.shared.livedata.EventObserver
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/*
 *  Copyright (c) 2021 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *   Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *   SPDX-License-Identifier: EUPL-1.2
 *
 */
class HolderMainActivity : AppCompatActivity() {

    private val introductionViewModel: IntroductionViewModel by viewModel()
    private val appStatusViewModel: AppConfigViewModel by viewModel()
    private val deviceRootedViewModel: DeviceRootedViewModel by viewModel()
    private val dialogUtil: DialogUtil by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.FLAVOR == "prod") {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val introductionStatus = introductionViewModel.getIntroductionStatus()
        if (introductionStatus !is IntroductionStatus.IntroductionFinished.NoActionRequired) {
            navController.navigate(
                R.id.action_introduction, IntroductionFragment.getBundle(introductionStatus)
            )
        }

        appStatusViewModel.appStatusLiveData.observe(this, {
            if (it !is AppStatus.NoActionRequired) {
                navController.navigate(R.id.action_app_status, AppStatusFragment.getBundle(it))
            }
        })

        deviceRootedViewModel.deviceRootedLiveData.observe(this, EventObserver {
            if (it) {
                dialogUtil.presentDialog(
                    context = this,
                    title = R.string.dialog_rooted_device_title,
                    message = getString(R.string.dialog_rooted_device_message),
                    positiveButtonText = R.string.dialog_rooted_device_positive_button,
                    positiveButtonCallback = { },
                    onDismissCallback = { deviceRootedViewModel.setHasDismissedRootedDeviceDialog() }
                )
            }
        })
    }

    override fun onStart() {
        super.onStart()
        // Only get app config on every app foreground when introduction is finished
        if (introductionViewModel.getIntroductionStatus() is IntroductionStatus.IntroductionFinished) {
            appStatusViewModel.refresh()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // Handle if an external app sets the launch mode of this activity to single top of single task.
        // In this case we need to set the graph again and handle the deeplink ourselves so that the entire
        // graph is traversed to find the deeplink
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        navController.setGraph(R.navigation.holder_nav_graph_root)
        navController.handleDeepLink(intent)
    }
}
