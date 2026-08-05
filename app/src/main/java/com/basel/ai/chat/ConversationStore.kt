package com.basel.ai.chat

import android.content.Context
import com.basel.ai.agent.ToolRun
import com.basel.ai.core.ErrorLog
import com.basel.ai.core.Severity
import com.basel.ai.web.SearchSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Conversations, on disk.
 *
 * Until now the entire history lived in one `StateFlow` and died with the
 * process — close the app, and everything said was gone. For a chat app that
 * is not a missing feature so much as a missing floor.
 *
 * One file per conversation rather than one file for all of them: saving after
 * every turn would otherwise rewrite the whole history each time, which grows
 * without bound and eventually stalls a turn on a big archive. A conversation
 * is written only when it changes.
 *
 * Thinking is *not* saved. It is working rather than answer, it is often
 * longer than the reply, and reloading a conversation to find pages of
 * discarded reasoning above every answer is worse than not having it.
 */
class ConversationStore(private val context: Context) {

    private val root: File get() = File(context.filesDir, "conversations").apply { mkdirs() }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    init {
        load()
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun find(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    /**
     * Writes a conversation, or removes it when nothing was ever said.
     *
     * An empty conversation is what "new chat" leaves behind when the user
     * changes their mind, and a list full of untitled empties is its own kind
     * of mess.
     */
    fun save(conversation: Conversation) {
        if (conversation.isEmpty) {
            delete(conversation.id)
            return
        }
        _conversations.value = ConversationIndex.sort(
            _conversations.value.filterNot { it.id == conversation.id } + conversation
        )
        runCatching { fileOf(conversation.id).writeText(encode(conversation)) }
            .onFailure { ErrorLog.report("Chat", "Could not save \"${conversation.title}\"", it) }
    }

    fun rename(id: String, title: String) {
        find(id)?.let { save(it.copy(title = title.trim().ifBlank { it.title })) }
    }

    fun delete(id: String) {
        _conversations.value = _conversations.value.filterNot { it.id == id }
        runCatching { fileOf(id).delete() }
    }

    fun deleteAll() {
        _conversations.value = emptyList()
        runCatching { root.listFiles()?.forEach { it.delete() } }
    }

    private fun fileOf(id: String) = File(root, "$id.json")

    private fun load() {
        val files = runCatching { root.listFiles { f -> f.extension == "json" } }.getOrNull()
            ?: return
        val loaded = files.mapNotNull { file ->
            runCatching { decode(JSONObject(file.readText())) }
                .onFailure {
                    // One corrupt file must not cost the other fifty.
                    ErrorLog.report(
                        "Chat", "Skipped ${file.name}", it, Severity.WARNING
                    )
                }
                .getOrNull()
        }
        _conversations.value = ConversationIndex.sort(loaded)
    }

    // ------------------------------------------------------------ encoding

    private fun encode(conversation: Conversation): String = JSONObject().apply {
        put("id", conversation.id)
        put("title", conversation.title)
        put("updatedAt", conversation.updatedAt)
        put(
            "messages",
            JSONArray().apply {
                conversation.messages
                    .filter { it.text.isNotBlank() }
                    .forEach { put(encodeMessage(it)) }
            }
        )
    }.toString()

    private fun encodeMessage(message: ChatMessage): JSONObject = JSONObject().apply {
        put("id", message.id)
        put("author", message.author.name)
        put("text", message.text)
        if (message.sources.isNotEmpty()) {
            put(
                "sources",
                JSONArray().apply {
                    message.sources.forEach {
                        put(
                            JSONObject().apply {
                                put("index", it.index)
                                put("title", it.title)
                                put("url", it.url)
                            }
                        )
                    }
                }
            )
        }
        if (message.toolRuns.isNotEmpty()) {
            put(
                "tools",
                JSONArray().apply {
                    message.toolRuns.forEach {
                        put(
                            JSONObject().apply {
                                put("tool", it.tool)
                                put("detail", it.detail)
                                put("output", it.output)
                                put("ok", it.ok)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun decode(json: JSONObject): Conversation {
        val array = json.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            ChatMessage(
                id = entry.optLong("id", i.toLong()),
                author = if (entry.optString("author") == Author.USER.name) {
                    Author.USER
                } else {
                    Author.MODEL
                },
                text = entry.optString("text"),
                sources = decodeSources(entry.optJSONArray("sources")),
                toolRuns = decodeTools(entry.optJSONArray("tools")),
            )
        }
        return Conversation(
            id = json.optString("id"),
            title = json.optString("title"),
            updatedAt = json.optLong("updatedAt"),
            messages = messages,
        )
    }

    private fun decodeSources(array: JSONArray?): List<SearchSource> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            SearchSource(
                index = entry.optInt("index", i + 1),
                title = entry.optString("title"),
                url = entry.optString("url"),
            )
        }
    }

    private fun decodeTools(array: JSONArray?): List<ToolRun> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            ToolRun(
                tool = entry.optString("tool"),
                detail = entry.optString("detail"),
                output = entry.optString("output"),
                ok = entry.optBoolean("ok", true),
            )
        }
    }
}
