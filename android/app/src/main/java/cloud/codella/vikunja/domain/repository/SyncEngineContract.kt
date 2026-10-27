package cloud.codella.vikunja.domain.repository

import cloud.codella.vikunja.domain.model.SyncResult

interface SyncEngineContract {
    suspend fun sync(): SyncResult
}
