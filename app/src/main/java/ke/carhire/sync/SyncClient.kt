package ke.carhire.sync

import android.content.Context
import androidx.room.withTransaction
import java.util.concurrent.TimeUnit
import ke.carhire.Evidence
import ke.carhire.LocalAttachment
import ke.carhire.Meta
import ke.carhire.data.files.AttachmentFiles
import ke.carhire.data.repository.Repository
import ke.carhire.security.Vault
import ke.carhire.util.amount
import ke.carhire.util.flag
import ke.carhire.util.json
import ke.carhire.util.now
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class SyncClient(
    private val repository: Repository,
    private val vault: Vault,
    private val context: Context = vault.context.applicationContext,
) {
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun endpoint(): String =
        requireNotNull(vault.get("backendUrl")) { "Configure a backend first" }
            .trimEnd('/')
            .also { require(it.startsWith("https://")) { "Backend must use HTTPS" } }

    private suspend fun call(path: String, body: JsonElement? = null): JsonObject =
        json
            .parseToJsonElement(
                String(
                    transport(
                        path,
                        body?.toString()?.toRequestBody("application/json".toMediaType()),
                    )
                )
            )
            .jsonObject

    private suspend fun transport(path: String, body: RequestBody? = null): ByteArray =
        withContext(Dispatchers.IO) {
            val authorization =
                Credentials.basic(
                    requireNotNull(vault.get("backendUser")),
                    requireNotNull(vault.get("backendPassword")),
                )
            val csrfRequest =
                Request.Builder()
                    .url(endpoint() + "/api/v1/auth/csrf")
                    .header("Authorization", authorization)
                    .build()
            var cookie = ""
            var token = ""
            var header = "X-CSRF-TOKEN"
            if (body != null)
                http.newCall(csrfRequest).execute().use { r ->
                    ensureSuccess(r)
                    cookie =
                        r.headers.values("Set-Cookie").joinToString("; ") {
                            it.substringBefore(';')
                        }
                    val t = json.parseToJsonElement(requireNotNull(r.body).string()).jsonObject
                    token = t.text("token")
                    header = t.text("headerName")
                }
            val builder =
                Request.Builder()
                    .url(endpoint() + "/api/v1" + path)
                    .header("Authorization", authorization)
            if (body != null) builder.header("Cookie", cookie).header(header, token).post(body)
            http.newCall(builder.build()).execute().use { r ->
                ensureSuccess(r)
                requireNotNull(r.body).bytes()
            }
        }

    private fun ensureSuccess(response: Response) {
        if (response.code == 429 || response.code >= 500)
            throw java.io.IOException(
                "Backend temporarily unavailable (${response.code}); retry later"
            )
        check(response.isSuccessful) {
            "Backend request failed (${response.code}); check profile and authorization"
        }
    }

    suspend fun run() = SyncGate.mutex.withLock { performRun() }

    private suspend fun performRun() {
        val dao = repository.dao
        for (original in dao.operations().filter { it.state in listOf("Pending", "Syncing") }) {
            if (dao.find(original.entityId)?.syncState in listOf("Conflict", "Error")) continue
            val payload = json.parseToJsonElement(original.payload).jsonObject.toMutableMap()
            if (original.attempts == 0)
                payload["baseRevision"] = JsonPrimitive(dao.find(original.entityId)?.revision ?: 0)
            val op =
                original.copy(
                    payload = JsonObject(payload).toString(),
                    state = "Syncing",
                    attempts = original.attempts + 1,
                )
            dao.putOperation(op)
            val response =
                call(
                        "/sync/push",
                        buildJsonObject {
                            put("installationId", repository.installation())
                            put("operations", JsonArray(listOf(JsonObject(payload))))
                        },
                    )
                    .getValue("results")
                    .jsonArray
                    .single()
                    .jsonObject
            repository.db.withTransaction {
                when (response.text("outcome")) {
                    "Accepted",
                    "Unchanged" -> {
                        val server = Repository.fromWire(response.getValue("record").jsonObject)
                        dao.deleteOperation(op.id)
                        val pending = dao.operations().any { it.entityId == op.entityId }
                        val local = dao.find(op.entityId)
                        dao.put(
                            if (pending && local != null)
                                local.copy(revision = server.revision, syncState = "Pending")
                            else server
                        )
                    }
                    "Conflict" -> {
                        val local = requireNotNull(dao.find(op.entityId))
                        dao.put(local.copy(syncState = "Conflict"))
                        dao.putOperation(
                            op.copy(
                                state = "Conflict",
                                error = "Revision conflict requires explicit resolution",
                            )
                        )
                        val server = response["server"]
                        if (server is JsonObject)
                            dao.evidence(Evidence(uuid(), "conflict", server.toString()))
                    }
                    "Rejected" -> {
                        dao.putOperation(op.copy(state = "Error", error = response.text("message")))
                        dao.find(op.entityId)?.let { dao.put(it.copy(syncState = "Error")) }
                    }
                    else -> error("Unknown sync outcome; no local acknowledgement committed")
                }
            }
        }
        var more = true
        while (more) {
            val cursor = dao.meta("cursor")?.value.orEmpty()
            val response = call("/sync/pull?cursor=$cursor")
            repository.applyPull(response.getValue("items").jsonArray, response.text("cursor"))
            more = response.flag("hasMore")
        }
        val storage = AttachmentFiles(context, repository)
        for (a in dao.attachments().filter { it.syncState == "Pending Upload" }) {
            if (dao.find(a.parentId)?.syncState != "Synced") continue
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        a.filename,
                        storage.bytes(a).toRequestBody(a.mime.toMediaType()),
                    )
                    .build()
            val result =
                json
                    .parseToJsonElement(
                        String(transport("/attachments?parentId=${a.parentId}&id=${a.id}", body))
                    )
                    .jsonObject
            require(result.text("sha256") == a.sha256) { "Uploaded attachment hash mismatch" }
            dao.attachment(a.copy(syncState = "Synced"))
        }
        var after = dao.meta("attachmentCursor")?.value ?: "0"
        do {
            val response = call("/attachments/changes?after=$after")
            val items = response.getValue("items").jsonArray
            for (item in items) {
                val n = item.jsonObject
                val a = attachmentFromWire(n)
                if (repository.dao.meta("purged:${a.parentId}")?.value?.isNotEmpty() == true)
                    continue
                val old = dao.attachments().find { it.id == a.id }
                require(old == null || old.sha256 == a.sha256) {
                    "Attachment UUID has conflicting content"
                }
                val bytes = transport("/attachments/${a.id}")
                require(bytes.size.toLong() == a.size && AttachmentFiles.detect(bytes) == a.mime)
                storage.store(bytes, a.sha256)
                dao.attachment(a)
            }
            after = response.text("cursor")
            dao.meta(Meta("attachmentCursor", after))
            more = response.flag("hasMore")
        } while (more)
        dao.meta(Meta("lastSync", now()))
        dao.meta(Meta("syncError", ""))
    }

    private fun attachmentFromWire(n: JsonObject) =
        LocalAttachment(
            id = n.text("id"),
            parentId = n.text("parentId"),
            filename = n.text("filename"),
            mime = n.text("mime"),
            size = n.amount("size"),
            sha256 = n.text("sha256"),
            syncState = "Synced",
        )
}
