package ke.carhire

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM attachments") suspend fun attachments(): List<LocalAttachment>

    @Upsert suspend fun attachment(value: LocalAttachment)

    @Query("DELETE FROM attachments") suspend fun clearAttachments()

    @Query("SELECT * FROM records ORDER BY updatedAt DESC") fun observe(): Flow<List<LocalRecord>>

    @Query("SELECT * FROM records ORDER BY kind,id") suspend fun all(): List<LocalRecord>

    @Query("SELECT * FROM records WHERE id=:id") suspend fun find(id: String): LocalRecord?

    @Upsert suspend fun put(row: LocalRecord)

    @Query("DELETE FROM records") suspend fun clearRecords()

    @Query("SELECT * FROM outbox ORDER BY rowid") suspend fun operations(): List<Operation>

    @Upsert suspend fun putOperation(operation: Operation)

    @Query("DELETE FROM outbox WHERE id=:id") suspend fun deleteOperation(id: String)

    @Query("DELETE FROM outbox") suspend fun clearOperations()

    @Query("SELECT * FROM evidence ORDER BY createdAt DESC") suspend fun evidence(): List<Evidence>

    @Insert suspend fun evidence(item: Evidence)

    @Query("DELETE FROM evidence") suspend fun clearEvidence()

    @Query("SELECT * FROM meta WHERE `key`=:key") suspend fun meta(key: String): Meta?

    @Query("SELECT * FROM meta") suspend fun allMeta(): List<Meta>

    @Upsert suspend fun meta(value: Meta)

    @Query("DELETE FROM meta") suspend fun clearMeta()
}
