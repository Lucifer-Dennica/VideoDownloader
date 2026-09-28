package com.example.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videodownloader.data.settings.SettingsRepository
import com.example.videodownloader.data.settings.ThemeMode
import com.example.videodownloader.ui.screens.*
import com.example.videodownloader.ui.theme.VideoDownloaderTheme
import com.example.videodownloader.ui.viewmodel.DownloadViewModel
import com.example.videodownloader.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val sharedLinkState = mutableStateOf("")

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private var needsMediaPermission = mutableStateOf(false)

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "Уведомления: granted=$granted")
        requestMediaPermissions()
    }

    private val mediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (perm, granted) ->
            Log.d(TAG, "Медиа $perm: granted=$granted")
        }
        val allGranted = result.values.all { it }
        needsMediaPermission.value = !allGranted

        if (!allGranted) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_VIDEO)) {
                needsMediaPermission.value = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestMediaPermissions()
        }

        handleShareIntent(intent)

        CoroutineScope(Dispatchers.IO).launch {
            val update = UpdateChecker.checkForUpdate()
            if (update != null) {
                runOnUiThread {
                    UpdateChecker.showUpdateDialog(this@MainActivity, update)
                }
            }
        }

        setContent {
            val settings = remember { SettingsRepository(this) }
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            VideoDownloaderTheme(themeMode = themeMode) {
                if (needsMediaPermission.value) {
                    AlertDialog(
                        onDismissRequest = { needsMediaPermission.value = false },
                        title = { Text("Нужно разрешение") },
                        text = {
                            Text(
                                "Чтобы приложение видело скачанные видео и фото, " +
                                "разрешите доступ к файлам в настройках."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                needsMediaPermission.value = false
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            }) { Text("Открыть настройки") }
                        },
                        dismissButton = {
                            TextButton(onClick = { needsMediaPermission.value = false }) {
                                Text("Позже")
                            }
                        }
                    )
                }

                val sharedLink = sharedLinkState.value

                VideoDownloaderRoot(sharedLink, { folderPicker.launch(null) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
            if (text.isNotBlank()) {
                sharedLinkState.value = text
                Log.d(TAG, "Share intent: $text")
            }
        }
    }

    private fun requestMediaPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                if (Build.VERSION.SDK_INT >= 34) {
                    add("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
                }
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            Log.d(TAG, "Все разрешения уже выданы")
            needsMediaPermission.value = false
            return
        }

        Log.d(TAG, "Запрашиваем разрешения: ${notGranted.toList()}")
        mediaPermissions.launch(notGranted.toTypedArray())
    }
}

data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun VideoDownloaderRoot(
    sharedLink: String,
    onChooseFolder: () -> Unit,
    vm: DownloadViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    val tabs = listOf(
        TabItem("Главная", Icons.Default.Home),
        TabItem("Загрузки", Icons.Default.Download),
        TabItem("Настройки", Icons.Default.Settings)
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val active by vm.activeItems.collectAsState()
    val completed by vm.completedItems.collectAsState()

    val badgeQueue by settings.badgeQueue.collectAsState(initial = false)
    val badgeTotal by settings.badgeTotal.collectAsState(initial = false)

    LaunchedEffect(Unit) {
        delay(800)
        vm.autoScan()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    val badgeCount: Int? = when {
                        i == 0 && badgeQueue -> active.size
                        i == 1 && badgeTotal -> completed.size
                        else -> null
                    }

                    NavigationBarItem(
                        selected = pagerState.currentPage == i,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(i) }
                        },
                        icon = {
                            if (badgeCount != null) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(badgeCount.toString())
                                        }
                                    }
                                ) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { pad ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            modifier = Modifier.padding(pad).fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    initialLink = sharedLink,
                    activeItems = active,
                    onDownloadVideo = { vm.enqueue(it, audio = false) },
                    onDownloadAudio = { vm.enqueue(it, audio = true) },
                    onDownloadMany = { vm.enqueueMany(it, audio = false) },
                    onDelete = vm::delete
                )
                1 -> DownloadsScreen(
                    items = completed,
                    onDelete = vm::delete,
                    onDeleteMany = vm::deleteMany,
                    onRetry = vm::retry,
                    onRescan = vm::rescanFolder
                )
                else -> SettingsScreen(
                    onChooseFolder = onChooseFolder,
                    vm = vm
                )
            }
        }
    }
}
