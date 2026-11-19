package ke.carhire

import android.app.Application
import androidx.room.Room
import ke.carhire.data.local.AppearancePreferences
import ke.carhire.data.repository.Repository
import ke.carhire.security.AppSession

class CarHireApp : Application() {
    val session = AppSession()
    val database by lazy {
        Room.databaseBuilder(this, CarDatabase::class.java, "car-hire.db").build()
    }
    val repository by lazy { Repository(database) }
    val appearance by lazy { AppearancePreferences(this) }

    override fun onCreate() {
        super.onCreate()
        ReminderWorker.schedule(this)
        SyncWorker.schedule(this)
    }
}
