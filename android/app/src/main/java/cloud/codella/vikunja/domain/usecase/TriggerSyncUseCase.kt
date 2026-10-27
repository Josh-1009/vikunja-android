package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.model.SyncResult
import cloud.codella.vikunja.domain.repository.SyncEngineContract

class TriggerSyncUseCase(
    private val syncEngine: SyncEngineContract
) {
    suspend operator fun invoke(): SyncResult = syncEngine.sync()
}
