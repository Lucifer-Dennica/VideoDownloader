package com.example.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videodownloader.ui.screens.*
import com.example.videodownloader.ui.theme.VideoDownloaderTheme
import com.example.videodownloader.ui.viewmodel.DownloadViewModel
import com.example.videodownloader.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var needsMediaPermission = mutableStateOf(false)

    // Запрос сразу НЕСКОЛЬКИХ разрешений
    private val mediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Если хотя бы одно отклонено — показать диалог
        val allGranted = result.values.all { it }
        needsMediaPermission.value = !allGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Список нужных разрешений на медиа
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Проверяем, все ли выданы
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            mediaPermissions.launch(permissionsToRequest)
        }

        // Проверка обновлений в фоне
        CoroutineScope(Dispatchers.IO).launch {
            val update = UpdateChecker.checkForUpdate()
            if (update != null) {
                runOnUiThread {
                    UpdateChecker.showUpdateDialog(this@MainActivity, update)
                }
            }
        }

        val shared = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else ""

        setContent {
            VideoDownloaderTheme {
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

                VideoDownloaderRoot(shared, { folderPicker.launch(null) })
            }
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun VideoDownloaderRoot(
    sharedLink: String,
    onChooseFolder: () -> Unit,
    vm: DownloadViewModel = viewModel()
) {
    val tabs = listOf(
        TabItem("Главная", Icons.Default.Home),
        TabItem("Загрузки", Icons.Default.Download),
        TabItem("Настройки", Icons.Default.Settings)
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val active by vm.activeItems.collectAsState()
    val completed by vm.completedItems.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == i,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(i) }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { pad ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(pad).fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(sharedLink, active, vm::enqueue, vm::delete)
                1 -> DownloadsScreen(completed, vm::delete, vm::rescanFolder)
                else -> SettingsScreen(onChooseFolder)
            }
        }
    }
}
