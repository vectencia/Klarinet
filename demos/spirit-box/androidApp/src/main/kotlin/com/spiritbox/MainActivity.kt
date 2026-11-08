package com.spiritbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.spiritbox.data.bindMicPermission
import com.spiritbox.data.releaseMicPermission
import com.spiritbox.platform.bindPlaybackContext

class MainActivity : ComponentActivity() {
    private var micCallback: ((Boolean) -> Unit)? = null
    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val callback = micCallback
        micCallback = null
        callback?.invoke(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindPlaybackContext(this)
        bindMicPermission(
            allowed = {
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            },
            ask = { callback ->
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    callback(true)
                } else {
                    micCallback = callback
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        micCallback = null
        releaseMicPermission()
        super.onDestroy()
    }
}
