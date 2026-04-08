package com.vectencia.koboe.demo

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vectencia.koboe.AudioEngine
import com.vectencia.koboe.AudioFileInfo
import com.vectencia.koboe.AudioFileReader
import com.vectencia.koboe.AudioStream
import com.vectencia.koboe.playFile

@Composable
fun FilePlayerScreen() {
    var filePath by remember { mutableStateOf("") }
    var fileInfo by remember { mutableStateOf<AudioFileInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf<AudioEngine?>(null) }
    var stream by remember { mutableStateOf<AudioStream?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                stream?.stop()
                stream?.close()
            } catch (_: Exception) {}
            try {
                engine?.release()
            } catch (_: Exception) {}
        }
    }

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
            value = filePath,
            onValueChange = { filePath = it },
            label = { Text("File Path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                errorMessage = null
                fileInfo = null
                try {
                    val reader = AudioFileReader(filePath)
                    fileInfo = reader.info
                    reader.close()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to load file"
                }
            },
            enabled = filePath.isNotBlank(),
        ) {
            Text("Load")
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
            )
        }

        fileInfo?.let { info ->
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
                onClick = {
                    if (isPlaying) {
                        try {
                            stream?.stop()
                            stream?.close()
                        } catch (_: Exception) {}
                        try {
                            engine?.release()
                        } catch (_: Exception) {}
                        stream = null
                        engine = null
                        isPlaying = false
                    } else {
                        try {
                            val newEngine = AudioEngine.create()
                            engine = newEngine
                            val newStream = newEngine.playFile(filePath)
                            stream = newStream
                            newStream.start()
                            isPlaying = true
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Playback failed"
                            isPlaying = false
                        }
                    }
                },
            ) {
                Text(if (isPlaying) "Stop" else "Play")
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
