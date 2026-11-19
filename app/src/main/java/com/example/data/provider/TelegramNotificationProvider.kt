package com.example.data.provider

import com.example.core.NotificationProvider
import com.example.data.prefs.LocalSecretsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TelegramInboundCommand(
    val updateId: Long,
    val command: String
)

class TelegramNotificationProvider(
    private val secretsStore: LocalSecretsStore,
    private val localScheduler: LocalReminderScheduler
) : NotificationProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override val providerName: String = "Telegram Bot API"

    override suspend fun send(recipient: String, title: String, message: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val token = secretsStore.getTelegramBotToken()
                val chatId = recipient.ifBlank { secretsStore.getTelegramChatId() }
                if (token.isBlank() || chatId.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Telegram bot token or chat ID not configured."))
                }
                val text = if (title.isBlank()) message else "*${escapeMarkdown(title)}*\n$message"
                val payload = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("parse_mode", "Markdown")
                }.toString()

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$token/sendMessage")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Telegram HTTP ${response.code}: $body"))
                    }
                    val json = JSONObject(body)
                    if (!json.optBoolean("ok", false)) {
                        return@withContext Result.failure(Exception(json.optString("description", "Telegram API error")))
                    }
                    Result.success(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun schedule(
        id: String,
        triggerAtMillis: Long,
        title: String,
        message: String
    ): Result<Unit> {
        return localScheduler.scheduleReminder(id, triggerAtMillis, title, message)
    }

    override suspend fun cancel(id: String): Result<Unit> {
        return localScheduler.cancelReminder(id)
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = secretsStore.getTelegramBotToken()
            if (token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Awaiting Telegram Bot Token"))
            }
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/getMe")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Telegram HTTP ${response.code}"))
                }
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) {
                    return@withContext Result.failure(Exception(json.optString("description", "Invalid bot token")))
                }
                val botUsername = json.optJSONObject("result")?.optString("username").orEmpty()
                Result.success("@$botUsername")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollCommands(): Result<List<TelegramInboundCommand>> = withContext(Dispatchers.IO) {
        try {
            val token = secretsStore.getTelegramBotToken()
            val expectedChatId = secretsStore.getTelegramChatId()
            if (token.isBlank() || expectedChatId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Telegram credentials not configured."))
            }
            val lastId = secretsStore.getLastTelegramUpdateId()
            val offsetParam = if (lastId > 0L) "?offset=${lastId + 1}&limit=10" else "?limit=10"
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/getUpdates$offsetParam")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Telegram HTTP ${response.code}"))
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) return@withContext Result.failure(Exception("Telegram getUpdates failed"))
                val arr = json.optJSONArray("result") ?: return@withContext Result.success(emptyList())
                val commands = mutableListOf<TelegramInboundCommand>()
                var maxId = lastId
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val uid = item.optLong("update_id", 0L)
                    if (uid > maxId) maxId = uid
                    val msg = item.optJSONObject("message") ?: continue
                    val chatId = msg.optJSONObject("chat")?.opt("id")?.toString().orEmpty()
                    val text = msg.optString("text", "").trim()
                    if (chatId == expectedChatId && text.startsWith("/")) {
                        commands += TelegramInboundCommand(uid, text.substringBefore(" ").lowercase())
                    }
                }
                if (maxId > lastId) secretsStore.saveLastTelegramUpdateId(maxId)
                Result.success(commands)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeMarkdown(raw: String): String =
        raw.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`")
}
