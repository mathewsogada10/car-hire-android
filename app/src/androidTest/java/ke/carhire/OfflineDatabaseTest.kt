package ke.carhire

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.ZipInputStream
import ke.carhire.data.files.DataFiles
import ke.carhire.data.repository.Repository
import ke.carhire.util.json
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDatabaseTest {
    private lateinit var db: CarDatabase
    private lateinit var repository: Repository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CarDatabase::class.java).build()
        repository = Repository(db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun verifiedPurgeKeepsMastersAndDoesNotSilentlyResurrect() = runBlocking {
        val vehicle = uuid()
        val customer = uuid()
        val hire = uuid()
        repository.save(
            "vehicles",
            vehicle,
            json
                .parseToJsonElement(
                    """{"registration":"TEST001","make":"Toyota","model":"Axio","status":"Available"}"""
                )
                .jsonObject,
        )
        repository.save(
            "customers",
            customer,
            buildJsonObject {
                put("name", "Purge customer")
                put("phone", "0700000000")
            },
        )
        repository.save(
            "hires",
            hire,
            buildJsonObject {
                put("vehicleId", vehicle)
                put("customerId", customer)
                put("status", "Completed")
                put("chargeMinor", 4000000)
                put("plannedStart", "2020-01-01T10:00:00Z")
                put("plannedEnd", "2020-01-02T10:00:00Z")
                put("actualStart", "2020-01-01T10:00:00Z")
                put("actualEnd", "2020-01-02T10:00:00Z")
            },
        )
        val original = requireNotNull(db.dao().find(hire))
        val files = DataFiles(context, repository)
        val exported = files.portable()
        val file = File(context.cacheDir, "purge-export.zip")
        files.write(Uri.fromFile(file), exported.second)
        files.verifyExportWritten(exported.first, Uri.fromFile(file))
        files.acknowledge(exported.first, "I VERIFIED AND SAFELY STORED THIS EXPORT")
        val preview = files.preview(exported.first, "2020-12-31")
        assertTrue(hire in preview.ids)
        files.purge(preview, "PURGE ${preview.ids.size}")
        assertEquals("{}", db.dao().find(hire)?.payload)
        assertNotNull(db.dao().find(customer))
        assertNotNull(db.dao().find(vehicle))
        assertTrue(db.dao().operations().none { it.entityId == hire })
        repository.applyPull(JsonArray(listOf(original.wire())), "checkpoint")
        assertEquals("{}", db.dao().find(hire)?.payload)
        assertTrue(file.delete())
    }

    @Test
    fun writesAndOutboxAreAtomicAndInvalidSaveDoesNotCommit() = runBlocking {
        val id = uuid()
        repository.save("customers", id, buildJsonObject { put("name", "Offline customer") })
        assertEquals(1, db.dao().all().size)
        assertEquals(1, db.dao().operations().size)
        try {
            repository.save("vehicles", uuid(), buildJsonObject { put("registration", "") })
            fail("Should reject invalid vehicle")
        } catch (e: IllegalArgumentException) {
            assertEquals(1, db.dao().all().size)
            assertEquals(1, db.dao().operations().size)
        }
    }

    @Test
    fun backupRestoreAndPortableExportWorkWithoutNetwork() = runBlocking {
        val id = uuid()
        repository.save("customers", id, buildJsonObject { put("name", "Offline backup customer") })
        val files = DataFiles(context, repository)
        val encrypted = files.backup("test-recovery-passphrase".toCharArray())
        assertNull(db.dao().meta("lastBackup"))
        try {
            files.writeBackup(Uri.parse("content://ke.carhire.nonexistent/backup"), encrypted)
            fail("Missing destination must fail")
        } catch (e: java.io.FileNotFoundException) {
            assertNull(db.dao().meta("lastBackup"))
        }
        val confirmed = File(context.cacheDir, "confirmed.chbk")
        files.writeBackup(Uri.fromFile(confirmed), encrypted)
        assertNotNull(db.dao().meta("lastBackup"))
        assertTrue(confirmed.delete())
        val row = requireNotNull(db.dao().find(id))
        repository.save("customers", id, buildJsonObject { put("name", "Changed") }, row.updatedAt)
        val safety = File(context.cacheDir, "test-safety.chbk")
        files.restore(encrypted, "test-recovery-passphrase".toCharArray(), Uri.fromFile(safety))
        assertEquals("Offline backup customer", db.dao().find(id)?.data()?.text("name"))
        assertTrue(safety.length() > 0)
        val portable = files.portable()
        val zip = File(context.cacheDir, "android-contract.zip")
        files.write(Uri.fromFile(zip), portable.second)
        files.verifyExportWritten(portable.first, Uri.fromFile(zip))
        val names = mutableListOf<String>()
        ZipInputStream(portable.second.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                names.add(entry.name)
            }
        }
        assertTrue(
            names.containsAll(
                listOf(
                    "manifest.json",
                    "data/vehicles.csv",
                    "data/customers.csv",
                    "data/records.jsonl",
                    "checksums.sha256",
                )
            )
        )
        assertTrue(safety.delete())
        assertTrue(zip.length() > 0)
    }

    @Test
    fun wrongBackupPassphraseNeverReplacesDatabase() = runBlocking {
        val id = uuid()
        repository.save("customers", id, buildJsonObject { put("name", "Preserve me") })
        val files = DataFiles(context, repository)
        val bytes = files.backup("correct-long-passphrase".toCharArray())
        try {
            files.restore(
                bytes,
                "wrong-long-passphrase".toCharArray(),
                Uri.fromFile(File(context.cacheDir, "unused.chbk")),
            )
            fail("Should reject wrong passphrase")
        } catch (e: javax.crypto.AEADBadTagException) {
            assertEquals("Preserve me", db.dao().find(id)?.data()?.text("name"))
        }
    }
}
