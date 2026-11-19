package ke.carhire.sync

import kotlinx.coroutines.sync.Mutex

object SyncGate {
    val mutex = Mutex()
}
