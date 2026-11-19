package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.baddieDataStore: DataStore<Preferences> by preferencesDataStore(name = "baddie_private_secrets")

class LocalSecretsStore(private val context: Context) {
    private val telegramBotTokenKey = stringPreferencesKey("telegram_bot_token")
    private val telegramChatIdKey = stringPreferencesKey("telegram_chat_id")
    private val uptimeApiKey = stringPreferencesKey("uptimerobot_api_key")
    private val lastTelegramUpdateIdKey = longPreferencesKey("telegram_last_update_id")

    val telegramBotToken: Flow<String> = context.baddieDataStore.data.map { it[telegramBotTokenKey].orEmpty() }
    val telegramChatId: Flow<String> = context.baddieDataStore.data.map { it[telegramChatIdKey].orEmpty() }
    val uptimeApiKeyFlow: Flow<String> = context.baddieDataStore.data.map { it[uptimeApiKey].orEmpty() }

    suspend fun getTelegramBotToken(): String = telegramBotToken.first()
    suspend fun getTelegramChatId(): String = telegramChatId.first()
    suspend fun getUptimeApiKey(): String = uptimeApiKeyFlow.first()
    suspend fun getLastTelegramUpdateId(): Long = context.baddieDataStore.data.map { it[lastTelegramUpdateIdKey] ?: 0L }.first()

    suspend fun saveTelegramCredentials(token: String, chatId: String) {
        context.baddieDataStore.edit { prefs ->
            prefs[telegramBotTokenKey] = token.trim()
            prefs[telegramChatIdKey] = chatId.trim()
        }
    }

    suspend fun saveUptimeApiKey(apiKey: String) {
        context.baddieDataStore.edit { prefs ->
            prefs[uptimeApiKey] = apiKey.trim()
        }
    }

    suspend fun saveLastTelegramUpdateId(updateId: Long) {
        context.baddieDataStore.edit { prefs ->
            prefs[lastTelegramUpdateIdKey] = updateId
        }
    }
}
