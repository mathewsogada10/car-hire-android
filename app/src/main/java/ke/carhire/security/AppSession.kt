package ke.carhire.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSession {
    private val current = MutableStateFlow(false)
    val unlocked = current.asStateFlow()

    fun unlock() {
        current.value = true
    }

    fun lock() {
        current.value = false
    }
}
