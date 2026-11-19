package ke.carhire.data.local

import android.content.Context
import android.util.Log
import java.io.IOException
import ke.carhire.domain.model.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppearancePreferences(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("car-hire-appearance", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(readTheme())
    val theme = current.asStateFlow()

    private fun readTheme(): ThemePreference {
        val stored = preferences.getString("theme", null) ?: return ThemePreference.System
        return ThemePreference.entries.find { it.name == stored }
            ?: ThemePreference.System.also {
                Log.w("CarHireAppearance", "Unknown saved theme; using system default")
            }
    }

    suspend fun setTheme(theme: ThemePreference) {
        withContext(Dispatchers.IO) {
            if (!preferences.edit().putString("theme", theme.name).commit())
                throw IOException("Unable to save appearance. Please try again.")
            current.value = theme
        }
    }
}
