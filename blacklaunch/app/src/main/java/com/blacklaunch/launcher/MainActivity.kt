package com.blacklaunch.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blacklaunch.launcher.apps.AppIconCache
import com.blacklaunch.launcher.ui.BlackHomeScreen
import com.blacklaunch.launcher.ui.theme.BlackLaunchTheme
import com.blacklaunch.launcher.viewmodel.LauncherViewModel

class MainActivity : ComponentActivity() {
    private val app by lazy { application as BlackLaunchApp }
    private val repository by lazy { app.appRepository }

    private val viewModel: LauncherViewModel by viewModels {
        viewModelFactory { initializer { LauncherViewModel(repository, app.userAliasStore.aliases) } }
    }

    private val iconCache by lazy {
        val metrics = resources.displayMetrics
        AppIconCache(repository, metrics.densityDpi, (36 * metrics.density).toInt())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Прозорі системні панелі зі світлими іконками поверх чорного фону.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            BlackLaunchTheme {
                BlackHomeScreen(
                    state = state,
                    onOpenSearch = viewModel::openSearch,
                    onQueryChange = viewModel::updateQuery,
                    onClearQuery = viewModel::clearQuery,
                    onCloseSearch = viewModel::closeSearch,
                    onLaunch = { app ->
                        if (!viewModel.launch(app)) {
                            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    iconFor = iconCache::load,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        repository.refresh()
    }

    // Натискання Home, коли BlackLaunch уже відкритий, повертає чистий чорний екран.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.closeSearch()
    }

    // Після переходу в іншу програму пошук закривається; поворот екрана його не скидає.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.closeSearch()
    }
}
