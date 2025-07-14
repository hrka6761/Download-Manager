package ir.hrka.downloadmanager

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.utilities.FileCreationMode
import ir.hrka.download_manager.utilities.FileLocation
import ir.hrka.downloadmanager.ui.theme.DownloadManagerTheme

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppContent(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current as MainActivity
    val mainViewModel = MainViewModel()

    DownloadManagerTheme {
        Scaffold(
            modifier = modifier.fillMaxSize()
        ) { innerPaddings ->
            var url by remember { mutableStateOf("https://srp-ai-assistant.ir/ai_models/gemma-3n-E4B-it-int4.task") }
            var fileName by remember { mutableStateOf("gemma-3n-E4B-it-int4") }
            var fileSize by remember { mutableStateOf("4405655031") }
            var fileSuffix by remember { mutableStateOf("task") }
            var directoryName by remember { mutableStateOf("models") }
            var version by remember { mutableStateOf("1.0.0") }
            var selectedLocation by remember {
                mutableStateOf<FileLocation>(FileLocation.InternalStorage)
            }
            var selectedCreationMode by remember {
                mutableStateOf<FileCreationMode>(FileCreationMode.Overwrite)
            }
            var runInService by remember { mutableStateOf(false) }
            val downloadStatus by mainViewModel.downloadStatus.collectAsState()
            val downloadProgress by mainViewModel.downloadProgress.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted)
                    mainViewModel.startDownload(
                        activity = activity,
                        fileDataModel = FileDataModel(
                            fileUrl = url,
                            fileName = fileName,
                            fileExtension = fileSuffix,
                            fileDirName = directoryName,
                            fileVersion = version,
                            totalBytes = fileSize.toLong(),
                            accessToken = null,
                        ),
                        fileLocation = selectedLocation,
                        creationMode = selectedCreationMode,
                        runInService = runInService
                    )
            }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPaddings),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OutlinedTextField(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URl") }
                )

                OutlinedTextField(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") }
                )

                OutlinedTextField(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    value = fileSize,
                    onValueChange = { fileSize = it },
                    label = { Text("File Size") }
                )

                OutlinedTextField(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    value = fileSuffix,
                    onValueChange = { fileSuffix = it },
                    label = { Text("File Suffix") }
                )

                OutlinedTextField(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    value = directoryName,
                    onValueChange = { directoryName = it },
                    label = { Text("Directory Name") }
                )

                OutlinedTextField(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Version") }
                )

                ElevatedCard(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
                ) {
                    Text(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 8.dp),
                        text = "Select Location type.",
                        fontSize = 12.sp
                    )
                    FileLocation.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLocation = option }
                                .padding(start = 8.dp)
                        ) {
                            RadioButton(
                                selected = (option == selectedLocation),
                                onClick = { selectedLocation = option }
                            )
                            Text(text = option.name)
                        }
                    }
                }

                ElevatedCard(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
                ) {
                    Text(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        text = "Select creation mode.",
                        fontSize = 12.sp
                    )
                    FileCreationMode.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCreationMode = option }
                                .padding(start = 8.dp)
                        ) {
                            RadioButton(
                                selected = (option == selectedCreationMode),
                                onClick = { selectedCreationMode = option }
                            )
                            Text(text = option.name)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .clickable {
                            runInService = !runInService
                        }
                ) {
                    Checkbox(
                        checked = runInService,
                        onCheckedChange = { runInService = it }
                    )
                    Text(
                        text = "I want to run in the service.",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    enabled = url.isNotEmpty() &&
                            fileName.isNotEmpty() &&
                            fileSize.isNotEmpty() &&
                            fileSuffix.isNotEmpty() &&
                            directoryName.isNotEmpty() &&
                            version.isNotEmpty(),
                    onClick = {
                        if (mainViewModel.hasNotificationPermission(activity))
                            mainViewModel.startDownload(
                                activity = activity,
                                fileDataModel = FileDataModel(
                                    fileUrl = url,
                                    fileName = fileName,
                                    fileExtension = fileSuffix,
                                    fileDirName = directoryName,
                                    fileVersion = version,
                                    totalBytes = fileSize.toLong(),
                                    accessToken = null,
                                ),
                                fileLocation = selectedLocation,
                                creationMode = selectedCreationMode,
                                runInService = runInService
                            )
                        else
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) {
                    Text("Start Download")
                }
            }

            if (downloadStatus != DownloadStatus.None)
                AlertDialog(
                    modifier = modifier.fillMaxWidth(),
                    onDismissRequest = {},
                    confirmButton = {
                        when (downloadStatus) {
                            is DownloadStatus.None -> {}
                            is DownloadStatus.StartDownload -> {}
                            is DownloadStatus.Downloading -> {}
                            is DownloadStatus.DownloadSuccess -> {
                                Button(
                                    onClick = {
                                        mainViewModel.setDownloadStatus(DownloadStatus.None)
                                        mainViewModel.checkDownload(activity)
                                    }
                                ) {
                                    Text(
                                        text = "Check Download",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            is DownloadStatus.DownloadFailed -> {
                                Button(
                                    onClick = { mainViewModel.retryDownload() }
                                ) {
                                    Text(
                                        text = "Retry",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    },
                    dismissButton = {
                        when (downloadStatus) {
                            is DownloadStatus.None -> {}

                            is DownloadStatus.Downloading, DownloadStatus.StartDownload -> {
                                TextButton(
                                    onClick = { mainViewModel.cancelDownload() }
                                ) {
                                    Text("Cancel Download")
                                }
                            }

                            is DownloadStatus.DownloadSuccess, DownloadStatus.DownloadFailed -> {
                                TextButton(
                                    onClick = { mainViewModel.setDownloadStatus(DownloadStatus.None) }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    },
                    icon = {
                        Icon(
                            painterResource(R.drawable.download),
                            contentDescription = null
                        )
                    },
                    title = {
                        Text(
                            text =
                                when (downloadStatus) {
                                    is DownloadStatus.None -> {
                                        ""
                                    }

                                    is DownloadStatus.StartDownload -> {
                                        "Start Download"
                                    }

                                    is DownloadStatus.Downloading -> {
                                        "Downloading"
                                    }

                                    is DownloadStatus.DownloadSuccess -> {
                                        "Download Success"
                                    }

                                    is DownloadStatus.DownloadFailed -> {
                                        "Download Failed"
                                    }
                                }
                        )
                    },
                    text = {
                        when (downloadStatus) {
                            is DownloadStatus.None -> {}
                            is DownloadStatus.Downloading, DownloadStatus.StartDownload -> {
                                Column(
                                    modifier = modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    LinearProgressIndicator(
                                        modifier = modifier.fillMaxWidth(),
                                        progress = { downloadProgress }
                                    )

                                    Spacer(
                                        modifier = modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                    )

                                    Text(
                                        text = "${(downloadProgress * 100).toInt()} %",
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            is DownloadStatus.DownloadSuccess -> {
                                Text(text = "Download successfully done.")
                            }

                            is DownloadStatus.DownloadFailed -> {
                                Text(text = "Failed to download the file.")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 16.dp
                )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun AppContentPreview() {
    AppContent()
}