package com.example

import com.example.data.db.MemoryDao
import com.example.data.db.MemoryEntity
import com.example.data.db.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  private class FakeMemoryDao(private val list: MutableList<MemoryEntity>) : MemoryDao {
    override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = emptyFlow()
    override suspend fun getAllMemories(): List<MemoryEntity> = list
    override suspend fun insertMemory(memory: MemoryEntity) { list.add(memory) }
    override suspend fun insertAll(memories: List<MemoryEntity>) { list.addAll(memories) }
    override suspend fun searchMemories(query: String): List<MemoryEntity> =
      list.filter { it.content.contains(query, ignoreCase = true) || it.keywords.contains(query, ignoreCase = true) }
    override suspend fun deleteById(id: String) { list.removeAll { it.id == id } }
    override suspend fun clearAll() { list.clear() }
  }

  @Test
  fun testRetrieveRelevantMemories_tokenMatching() = runBlocking {
    val memories = mutableListOf(
      MemoryEntity(
        id = "1",
        content = "Morteza has a 15-year-old cousin named Radin who loves coding.",
        category = "FAMILY",
        importance = 4,
        keywords = "cousin, radin, coding",
        source = "USER_CONVERSATION",
        timestamp = 1000L
      ),
      MemoryEntity(
        id = "2",
        content = "The user's favorite programming language is Kotlin.",
        category = "GENERAL",
        importance = 5,
        keywords = "kotlin, programming",
        source = "USER_CONVERSATION",
        timestamp = 2000L
      )
    )

    val fakeDao = FakeMemoryDao(memories)
    val repo = MemoryRepository(fakeDao)

    // With limit = 1 (so all.size > limit forces search), check question:
    val result = repo.retrieveRelevantMemories("What is the name of my cousin?", limit = 1)
    assertEquals(1, result.size)
    assertEquals("1", result.first().id)
    assertTrue(result.first().content.contains("Radin"))
  }

  @Test
  fun testRetrieveRelevantMemories_smallVaultInjectAll() = runBlocking {
    val memories = mutableListOf(
      MemoryEntity(
        id = "1",
        content = "Morteza's cousin is Radin",
        category = "FAMILY",
        importance = 4,
        keywords = "cousin",
        source = "USER_CONVERSATION",
        timestamp = 1000L
      )
    )

    val fakeDao = FakeMemoryDao(memories)
    val repo = MemoryRepository(fakeDao)

    // With limit = 10 and 1 memory (all.size <= limit), all memories are returned as permanent context
    val result = repo.retrieveRelevantMemories("Hello there", limit = 10)
    assertEquals(1, result.size)
    assertEquals("1", result.first().id)
  }
}
