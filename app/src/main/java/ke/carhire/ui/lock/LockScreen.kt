package ke.carhire.ui.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import ke.carhire.security.Vault
import ke.carhire.ui.components.ErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val vault = remember { Vault(activity) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var exists by remember { mutableStateOf(vault.hasPin()) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Car Hire", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (exists) "Unlock your private business workspace"
            else "Create your owner PIN (6 to 12 digits)"
        )
        OutlinedTextField(
            pin,
            { pin = it },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                activity.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (!vault.hasPin()) vault.setPin(pin)
                            else require(vault.unlock(pin)) { "Incorrect PIN" }
                        }
                        pin = ""
                        exists = vault.hasPin()
                        onUnlocked()
                    } catch (e: Exception) {
                        error = e.message ?: "Unable to unlock secure storage"
                    } finally {
                        busy = false
                    }
                }
            },
        ) {
            Text(if (exists) "Unlock" else "Set PIN")
        }
        if (
            exists &&
                androidx.biometric.BiometricManager.from(activity)
                    .canAuthenticate(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        )
            OutlinedButton(
                onClick = {
                    val prompt =
                        BiometricPrompt(
                            activity,
                            ContextCompat.getMainExecutor(activity),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(
                                    result: BiometricPrompt.AuthenticationResult
                                ) {
                                    onUnlocked()
                                }

                                override fun onAuthenticationError(code: Int, text: CharSequence) {
                                    error = "$text. Use your PIN instead."
                                }
                            },
                        )
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Car Hire")
                            .setAllowedAuthenticators(
                                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                            )
                            .setNegativeButtonText("Use PIN")
                            .build()
                    )
                }
            ) {
                Text("Use biometric")
            }
    }
}
