package ke.carhire

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ke.carhire.util.json
import ke.carhire.util.now
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
@Entity(tableName = "records", indices = [Index("kind"), Index("updatedAt")])
data class LocalRecord(
    @PrimaryKey val id: String,
    val kind: String,
    val revision: Long = 0,
    val payload: String,
    val createdAt: String = now(),
    val updatedAt: String = now(),
    val deletedAt: String? = null,
    val syncState: String = "Pending",
) {
    fun data() = json.parseToJsonElement(payload).jsonObject

    fun wire() = buildJsonObject {
        put("id", id)
        put("kind", kind)
        put("revision", revision)
        put("data", data())
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("deletedAt", deletedAt?.let(::JsonPrimitive) ?: JsonNull)
    }
}

@Serializable
@Entity(tableName = "outbox", indices = [Index("entityId")])
data class Operation(
    @PrimaryKey val id: String,
    val entityId: String,
    val payload: String,
    val createdAt: String = now(),
    val state: String = "Pending",
    val attempts: Int = 0,
    val error: String? = null,
)

@Serializable
@Entity(tableName = "evidence")
data class Evidence(
    @PrimaryKey val id: String,
    val kind: String,
    val payload: String,
    val createdAt: String = now(),
)

@Serializable
@Entity(tableName = "meta")
data class Meta(@PrimaryKey val key: String, val value: String)

@Serializable
@Entity(tableName = "attachments", indices = [Index("parentId")])
data class LocalAttachment(
    @PrimaryKey val id: String,
    val parentId: String,
    val filename: String,
    val mime: String,
    val size: Long,
    val sha256: String,
    val syncState: String = "Pending Upload",
)
