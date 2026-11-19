package ke.carhire.data.files

import ke.carhire.Evidence
import ke.carhire.LocalAttachment
import ke.carhire.LocalRecord
import ke.carhire.Meta
import ke.carhire.Operation
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val format: String = "car-hire-encrypted-backup",
    val schema: Int = 1,
    val records: List<LocalRecord>,
    val outbox: List<Operation>,
    val evidence: List<Evidence>,
    val metadata: List<Meta>,
    val attachments: List<LocalAttachment> = emptyList(),
    val files: Map<String, String> = emptyMap(),
)
