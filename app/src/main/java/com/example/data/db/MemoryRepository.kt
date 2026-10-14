package com.example.data.db

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MemoryRepository(private val dao: MemoryDao) {

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemoriesFlow()

    suspend fun getAllMemories(): List<MemoryEntity> {
        return dao.getAllMemories()
    }

    suspend fun addMemoryNote(
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        importance: Int = 3,
        keywords: String = "",
        source: String = "USER_CONVERSATION"
    ) {
        val entity = MemoryEntity(
            id = UUID.randomUUID().toString(),
            content = content.trim(),
            category = category.name,
            importance = importance.coerceIn(1, 5),
            keywords = keywords.trim(),
            source = source,
            timestamp = System.currentTimeMillis()
        )
        dao.insertMemory(entity)
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        return dao.searchMemories(query)
    }

    suspend fun deleteMemoryNote(id: String) {
        dao.deleteById(id)
    }

    companion object {
        private val STOPWORDS = setOf(
            "the","what","whats","who","whos","which","when","where","why","how",
            "are","was","were","been","being","have","has","had","did","does","doing","done",
            "can","could","should","would","will","shall","may","might","must",
            "you","your","yours","they","them","their","we","our","us","he","she","his","her","him",
            "about","into","over","under","from","with","for","and","or","but","not","please",
            "tell","know","there","here","this","that","these","those"
        )
    }

    /** Token-based retrieval with small-vault inject-all and top-importance fallback. */
    suspend fun retrieveRelevantMemories(query: String, limit: Int): List<MemoryEntity> {
        val all = dao.getAllMemories()
            .sortedWith(compareByDescending<MemoryEntity> { it.importance }.thenByDescending { it.timestamp })
        if (all.isEmpty()) return emptyList()

        // Small vault = permanent context: always inject everything (no search needed)
        if (all.size <= limit) return all

        val tokens = query.lowercase()
            .split(Regex("[^a-z0-9']+"))          // strips punctuation like "cousin?"
            .filter { it.length >= 3 && it !in STOPWORDS }
            .distinct()

        val matched = all.filter { mem ->
            val haystack = (mem.content + " " + mem.keywords).lowercase()
            tokens.any { t -> haystack.contains(t) || haystack.contains(t.removeSuffix("s")) }
        }
        return (matched.ifEmpty { all }).take(limit)   // fallback: top facts always present
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
