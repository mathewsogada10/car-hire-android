package ke.carhire

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            LocalRecord::class,
            Operation::class,
            Evidence::class,
            Meta::class,
            LocalAttachment::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class CarDatabase : RoomDatabase() {
    abstract fun dao(): CarDao
}
