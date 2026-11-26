package com.geminidict.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geminidict.app.ui.screens.DictManagerScreen
import com.geminidict.app.ui.screens.ReviewScreen
import com.geminidict.app.ui.screens.SettingsScreen
import com.geminidict.app.ui.screens.TranslateScreen
import com.geminidict.app.ui.screens.WordbookScreen
import com.geminidict.app.ui.theme.GeminiDictTheme
import com.geminidict.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            GeminiDictTheme {
                GeminiDictAppShell(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_IMPORT_YOUDAO -> {
                val path = intent.getStringExtra("import_path")
                if (!path.isNullOrBlank()) {
                    viewModel.importYoudaoFromLocalPath(path)
                }
            }
            Intent.ACTION_SEND -> {
                val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (streamUri != null) {
                    viewModel.importYoudaoFromUri(streamUri, streamUri.lastPathSegment ?: "shared.xml")
                }
            }
        }
    }

    companion object {
        const val ACTION_IMPORT_YOUDAO = "com.geminidict.app.ACTION_IMPORT_YOUDAO"
    }
}

@Composable
fun GeminiDictAppShell(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val tabs = listOf(
        Triple("查词", Icons.Default.Search, 0),
        Triple("单词本", Icons.Default.List, 1),
        Triple("复习", Icons.Default.DateRange, 2),
        Triple("词库", Icons.Default.Build, 3),
        Triple("设置", Icons.Default.Settings, 4)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 2) {
                                viewModel.refreshReviewQueue()
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> TranslateScreen(viewModel)
                1 -> WordbookScreen(viewModel)
                2 -> ReviewScreen(viewModel)
                3 -> DictManagerScreen(viewModel)
                4 -> SettingsScreen(viewModel)
            }
        }
    }
}
