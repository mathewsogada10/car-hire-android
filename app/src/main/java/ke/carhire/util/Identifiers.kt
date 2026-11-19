package ke.carhire.util

import java.time.Instant
import java.util.UUID

fun now(): String = Instant.now().toString()

fun uuid(): String = UUID.randomUUID().toString()
