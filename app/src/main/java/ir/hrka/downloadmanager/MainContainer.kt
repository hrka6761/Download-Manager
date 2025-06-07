package ir.hrka.downloadmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hrka.download_manager.utilities.FileCreationMode
import ir.hrka.download_manager.utilities.FileLocation
import ir.hrka.downloadmanager.ui.theme.DownloadManagerTheme

@Composable
fun AppContent(modifier: Modifier = Modifier) {
    DownloadManagerTheme {
        Scaffold(
            modifier = modifier.fillMaxSize()
        ) { innerPaddings ->
            var url by remember { mutableStateOf("") }
            var fileName by remember { mutableStateOf("") }
            var fileSuffix by remember { mutableStateOf("") }
            var directoryName by remember { mutableStateOf("") }
            var version by remember { mutableStateOf("") }
            var selectedLocation by remember {
                mutableStateOf<FileLocation>(FileLocation.InternalStorage)
            }
            var selectedCreationMode by remember {
                mutableStateOf<FileCreationMode>(FileCreationMode.Overwrite)
            }
            var runInService by remember { mutableStateOf(false) }

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
                            .padding(start = 8.dp, top = 8.dp),
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
                        .padding(8.dp)
                        .clickable {
                            runInService = !runInService
                        } // Optional: make row clickable too
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
                        .padding(horizontal = 8.dp, vertical = 24.dp),
                    enabled = url.isNotEmpty() &&
                            fileName.isNotEmpty() &&
                            fileSuffix.isNotEmpty() &&
                            directoryName.isNotEmpty() &&
                            version.isNotEmpty(),
                    onClick = {}
                ) {
                    Text("Start Download")
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun AppContentPreview() {
    AppContent()
}