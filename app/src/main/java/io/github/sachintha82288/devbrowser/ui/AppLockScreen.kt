package io.github.sachintha82288.devbrowser.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sachintha82288.devbrowser.R
import io.github.sachintha82288.devbrowser.ui.theme.MonoTextStyle
import io.github.sachintha82288.devbrowser.ui.theme.NeonCyan
import java.util.concurrent.Executor

/**
 * Stage B: app lock.
 *
 * Uses the platform BiometricPrompt on Android 9+ and the device-credential
 * Keyguard intent on Android 8.x. If the device has no lock at all the gate is
 * skipped rather than trapping the user outside their own browser.
 */
@Composable
fun AppLockScreen(activity: Activity, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val keyguard = remember {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }
    var message by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executor { command -> Handler(Looper.getMainLooper()).post(command) } }
    val cancellation = remember { CancellationSignal() }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) onUnlocked() else message = null
    }

    fun askCredential() {
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            context.getString(R.string.lock_title),
            context.getString(R.string.lock_subtitle),
        )
        if (intent != null) {
            credentialLauncher.launch(intent)
        } else {
            // No secure lock configured: the app lock cannot protect anything.
            message = context.getString(R.string.lock_not_secure)
            onUnlocked()
        }
    }

    fun unlock() {
        val secure = keyguard?.isDeviceSecure == true
        if (!secure) {
            message = context.getString(R.string.lock_not_secure)
            onUnlocked()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(context.getString(R.string.lock_title))
                .setSubtitle(context.getString(R.string.lock_subtitle))
                .setNegativeButton(
                    context.getString(R.string.lock_use_pin),
                    executor,
                ) { _, _ -> askCredential() }
                .build()
            prompt.authenticate(
                cancellation,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult?,
                    ) {
                        onUnlocked()
                    }

                    override fun onAuthenticationError(code: Int, errString: CharSequence?) {
                        message = errString?.toString()
                    }
                },
            )
        } else {
            askCredential()
        }
    }

    LaunchedEffect(Unit) { unlock() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.lock_subtitle),
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = { unlock() }) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = null)
            Text(
                text = stringResource(R.string.lock_button),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        message?.let {
            Text(
                text = it,
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
