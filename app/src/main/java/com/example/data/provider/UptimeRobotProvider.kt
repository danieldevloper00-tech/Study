package com.example.data.provider

import com.example.core.UptimeMonitorItem
import com.example.core.UptimeMonitoringProvider
import com.example.core.UptimeStatusResult
import com.example.data.prefs.LocalSecretsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UptimeRobotProvider(
    private val secretsStore: LocalSecretsStore
) : UptimeMonitoringProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override val providerName: String = "UptimeRobot API v2"

    override suspend fun fetchMonitors(): UptimeStatusResult = withContext(Dispatchers.IO) {
        try {
            val apiKey = secretsStore.getUptimeApiKey()
            if (apiKey.isBlank()) {
                return@withContext UptimeStatusResult.AwaitingCredentials
            }

            val body = FormBody.Builder()
                .add("api_key", apiKey)
                .add("format", "json")
                .add("custom_uptime_ratios", "7-30")
                .add("response_times", "1")
                .add("response_times_limit", "1")
                .add("logs", "1")
                .add("logs_limit", "3")
                .build()

            val request = Request.Builder()
                .url("https://api.uptimerobot.com/v2/getMonitors")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext UptimeStatusResult.Error("UptimeRobot HTTP ${response.code}")
                }
                val json = JSONObject(raw)
                if (json.optString("stat") != "ok") {
                    val errMsg = json.optJSONObject("error")?.optString("message") ?: "Invalid UptimeRobot key"
                    return@withContext UptimeStatusResult.Error(errMsg)
                }
                val arr = json.optJSONArray("monitors")
                val list = mutableListOf<UptimeMonitorItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val m = arr.getJSONObject(i)
                        val status = m.optInt("status", 0)
                        val statusLabel = when (status) {
                            2 -> "Up"
                            8 -> "Seems Down"
                            9 -> "Down"
                            0 -> "Paused"
                            else -> "Checking"
                        }
                        val rtArr = m.optJSONArray("response_times")
                        val avgRt = if (rtArr != null && rtArr.length() > 0) {
                            rtArr.getJSONObject(0).optInt("value", 0)
                        } else 0
                        val logsArr = m.optJSONArray("logs")
                        val lastIncident = if (logsArr != null && logsArr.length() > 0) {
                            val first = logsArr.getJSONObject(0)
                            val reason = first.optJSONObject("reason")?.optString("detail").orEmpty()
                            reason.ifBlank { "Status event logged" }
                        } else "No recent incidents"

                        list += UptimeMonitorItem(
                            id = m.optLong("id", 0L).toString(),
                            friendlyName = m.optString("friendly_name", "Monitor"),
                            url = m.optString("url", ""),
                            statusCode = status,
                            statusLabel = statusLabel,
                            uptimeRatio = m.optString("custom_uptime_ratio", "100").substringBefore("-"),
                            averageResponseMs = avgRt,
                            lastIncidentSummary = lastIncident
                        )
                    }
                }
                UptimeStatusResult.Live(list, System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UptimeStatusResult.Error(e.localizedMessage ?: "Failed to query UptimeRobot")
        }
    }
}
