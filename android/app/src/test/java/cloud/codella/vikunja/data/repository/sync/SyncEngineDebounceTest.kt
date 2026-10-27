package cloud.codella.vikunja.data.repository.sync

import cloud.codella.vikunja.data.local.entity.SyncQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineDebounceTest {

    @Test
    fun `empty list returns empty result`() {
        val result = SyncEngine.coalesceMutations(emptyList())
        assertTrue(result.itemsToProcess.isEmpty())
        assertTrue(result.supersededItemIds.isEmpty())
    }

    @Test
    fun `single item returns without modification`() {
        val item = SyncQueueEntity(id = 1L, entityType = "TASK", entityId = 10L, operation = "UPDATE", payloadJson = "{}")
        val result = SyncEngine.coalesceMutations(listOf(item))
        assertEquals(listOf(item), result.itemsToProcess)
        assertTrue(result.supersededItemIds.isEmpty())
    }

    @Test
    fun `multiple rapid updates on same entity retain only latest update and drop superseded`() {
        val item1 = SyncQueueEntity(id = 101L, entityType = "TASK", entityId = 42L, operation = "UPDATE", payloadJson = """{"title":"v1"}""")
        val item2 = SyncQueueEntity(id = 102L, entityType = "TASK", entityId = 42L, operation = "UPDATE", payloadJson = """{"title":"v2"}""")
        val item3 = SyncQueueEntity(id = 103L, entityType = "TASK", entityId = 42L, operation = "UPDATE", payloadJson = """{"title":"v3"}""")

        val result = SyncEngine.coalesceMutations(listOf(item1, item2, item3))

        assertEquals(listOf(item3), result.itemsToProcess)
        assertEquals(listOf(101L, 102L), result.supersededItemIds)
    }

    @Test
    fun `update followed by delete drops update and keeps delete`() {
        val updateItem = SyncQueueEntity(id = 201L, entityType = "TASK", entityId = 55L, operation = "UPDATE", payloadJson = """{"done":true}""")
        val deleteItem = SyncQueueEntity(id = 202L, entityType = "TASK", entityId = 55L, operation = "DELETE", payloadJson = "{}")

        val result = SyncEngine.coalesceMutations(listOf(updateItem, deleteItem))

        assertEquals(listOf(deleteItem), result.itemsToProcess)
        assertEquals(listOf(201L), result.supersededItemIds)
    }

    @Test
    fun `offline local-only task created then deleted drops entire mutation chain`() {
        val createItem = SyncQueueEntity(id = 301L, entityType = "TASK", entityId = -1L, operation = "CREATE", payloadJson = """{"title":"Temporary"}""")
        val updateItem = SyncQueueEntity(id = 302L, entityType = "TASK", entityId = -1L, operation = "UPDATE", payloadJson = """{"title":"Changed"}""")
        val deleteItem = SyncQueueEntity(id = 303L, entityType = "TASK", entityId = -1L, operation = "DELETE", payloadJson = "{}")

        val result = SyncEngine.coalesceMutations(listOf(createItem, updateItem, deleteItem))

        assertTrue(result.itemsToProcess.isEmpty())
        assertEquals(listOf(301L, 302L, 303L), result.supersededItemIds)
    }

    @Test
    fun `offline local-only task created then multiple updates keeps create and latest update`() {
        val createItem = SyncQueueEntity(id = 401L, entityType = "TASK", entityId = -2L, operation = "CREATE", payloadJson = """{"title":"New Task"}""")
        val update1 = SyncQueueEntity(id = 402L, entityType = "TASK", entityId = -2L, operation = "UPDATE", payloadJson = """{"title":"Updated 1"}""")
        val update2 = SyncQueueEntity(id = 403L, entityType = "TASK", entityId = -2L, operation = "UPDATE", payloadJson = """{"title":"Updated 2"}""")

        val result = SyncEngine.coalesceMutations(listOf(createItem, update1, update2))

        assertEquals(listOf(createItem, update2), result.itemsToProcess)
        assertEquals(listOf(402L), result.supersededItemIds)
    }

    @Test
    fun `interleaved operations across different entities preserve relative execution order`() {
        val taskA1 = SyncQueueEntity(id = 501L, entityType = "TASK", entityId = 1L, operation = "UPDATE", payloadJson = "A1")
        val taskB1 = SyncQueueEntity(id = 502L, entityType = "TASK", entityId = 2L, operation = "UPDATE", payloadJson = "B1")
        val taskA2 = SyncQueueEntity(id = 503L, entityType = "TASK", entityId = 1L, operation = "UPDATE", payloadJson = "A2")
        val projectC = SyncQueueEntity(id = 504L, entityType = "PROJECT", entityId = 3L, operation = "CREATE", payloadJson = "C")

        val result = SyncEngine.coalesceMutations(listOf(taskA1, taskB1, taskA2, projectC))

        assertEquals(listOf(taskB1, taskA2, projectC), result.itemsToProcess)
        assertEquals(listOf(501L), result.supersededItemIds)
    }
}
