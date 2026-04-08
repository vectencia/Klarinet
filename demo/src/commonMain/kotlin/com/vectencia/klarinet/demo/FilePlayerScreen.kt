package com.vectencia.klarinet.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FilePlayerScreen(viewModel: FilePlayerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text("File Player", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.filePath,
            onValueChange = { viewModel.onEvent(FilePlayerEvent.FilePathChanged(it)) },
            label = { Text("File Path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.onEvent(FilePlayerEvent.LoadFile) },
            enabled = state.filePath.isNotBlank(),
        ) {
            Text("Load")
        }

        state.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
            )
        }

        state.fileInfo?.let { info ->
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("File Info", fontSize = 18.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    InfoRow("Format", info.format.name)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoRow("Duration", formatDuration(info.durationMs))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoRow("Sample Rate", "${info.sampleRate} Hz")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoRow("Channels", "${info.channelCount}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InfoRow("Bit Rate", "${info.bitRate / 1000} kbps")
                }
            }

            val tags = info.tags
            if (tags.title != null || tags.artist != null || tags.album != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text("Tags", fontSize = 18.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        tags.title?.let { title ->
                            InfoRow("Title", title)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                        tags.artist?.let { artist ->
                            InfoRow("Artist", artist)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                        tags.album?.let { album ->
                            InfoRow("Album", album)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onEvent(FilePlayerEvent.TogglePlayback) },
            ) {
                Text(if (state.isPlaying) "Stop" else "Play")
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 14.sp)
    }
}
