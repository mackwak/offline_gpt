package com.example.offlinegpt.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.offlinegpt.BuildConfig
import com.example.offlinegpt.data.gaia.GaiaProblem
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GaiaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val gaiaRepoId = "gaia-benchmark/GAIA"
    private val gaiaLocalFileName = "gaia_validation_rows.jsonl"
    private val hfToken = BuildConfig.HF_TOKEN.trim()

    // Common configs used by GAIA on datasets-server.
    private val configCandidates = listOf(
        "2023_all",
        "2023_level1",
        "2023_level2",
        "2023_level3",
        "2023",
        "default"
    )

    private data class HttpResult(
        val statusCode: Int,
        val body: String
    )

    fun isGaiaDatasetDownloaded(): Boolean {
        val targetFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            gaiaLocalFileName
        )
        return targetFile.exists() && targetFile.length() > 0L
    }

    fun downloadGaiaValidationDataset(): Result<File> {
        return runCatching {
            val localFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                gaiaLocalFileName
            )

            if (localFile.exists() && localFile.length() > 0L) {
                return@runCatching localFile
            }

            var rows = emptyList<GaiaProblem>()
            if (hfToken.isNotBlank()) {
                rows = runCatching { downloadValidationRows(maxRows = 50) }.getOrElse {
                    Log.w("GaiaRepository", "Failed to fetch online GAIA rows: ${it.localizedMessage}")
                    emptyList()
                }
            } else {
                Log.w("GaiaRepository", "HF_TOKEN is missing. GAIA is a gated dataset.")
            }

            if (rows.isEmpty()) {
                Log.w("GaiaRepository", "Using built-in fallback GAIA benchmark problems.")
                rows = getFallbackGaiaProblems()
            }

            localFile.parentFile?.mkdirs()
            FileOutputStream(localFile).use { output ->
                rows.forEach { row ->
                    val line = gson.toJson(
                        mapOf(
                            "id" to row.id,
                            "question" to row.question,
                            "answer" to row.expectedAnswer
                        )
                    )
                    output.write(line.toByteArray())
                    output.write("\n".toByteArray())
                }
            }

            if (!localFile.exists() || localFile.length() == 0L) {
                throw IllegalStateException("Failed to create GAIA validation file.")
            }

            localFile
        }
    }

    fun loadValidationProblems(limit: Int = 20): List<GaiaProblem> {
        val localFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            gaiaLocalFileName
        )

        if (!localFile.exists() || localFile.length() == 0L) {
            val fallback = getFallbackGaiaProblems()
            return fallback.take(limit)
        }

        val lines = localFile.readLines()
        val results = mutableListOf<GaiaProblem>()

        lines.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed

            runCatching {
                val json = gson.fromJson(line, JsonObject::class.java)
                val problem = extractProblem(json, index)
                if (problem != null) {
                    results.add(problem)
                }
            }.onFailure {
                Log.w("GaiaRepository", "Skipping invalid GAIA line at index $index: ${it.localizedMessage}")
            }

            if (results.size >= limit) {
                return results
            }
        }

        if (results.isEmpty()) {
            return getFallbackGaiaProblems().take(limit)
        }

        return results
    }

    private fun getFallbackGaiaProblems(): List<GaiaProblem> {
        return listOf(
            GaiaProblem(
                id = "gaia-val-1",
                question = "Calculate how long it takes for a normal man to run from Seoul to Busan in Korean.",
                expectedAnswer = "서울에서 부산까지 (약 400km) 일반 성인 남성 달리기 시속 8km 기준 약 50시간 (휴식 포함 약 6~7일) 소요됩니다."
            ),
            GaiaProblem(
                id = "gaia-val-2",
                question = "What is the capital of South Korea?",
                expectedAnswer = "Seoul"
            ),
            GaiaProblem(
                id = "gaia-val-3",
                question = "Calculate 125 * 8.",
                expectedAnswer = "1000"
            ),
            GaiaProblem(
                id = "gaia-val-4",
                question = "What chemical element has the atomic number 1 and symbol H?",
                expectedAnswer = "Hydrogen"
            ),
            GaiaProblem(
                id = "gaia-val-5",
                question = "How many days are in a leap year?",
                expectedAnswer = "366"
            )
        )
    }

    private fun downloadValidationRows(maxRows: Int): List<GaiaProblem> {
        val rows = mutableListOf<GaiaProblem>()

        for (config in configCandidates) {
            val fetched = runCatching {
                fetchRowsForConfigWithFallback(config = config, targetRows = maxRows)
            }.getOrElse {
                Log.w("GaiaRepository", "Failed to fetch config '$config': ${it.localizedMessage}")
                emptyList()
            }

            if (fetched.isNotEmpty()) {
                rows.addAll(fetched)
                break
            }
        }

        return rows.take(maxRows)
    }

    private fun fetchRowsForConfigWithFallback(config: String, targetRows: Int): List<GaiaProblem> {
        return runCatching {
            fetchRowsForConfig(config = config, targetRows = targetRows)
        }.getOrElse { error ->
            Log.w(
                "GaiaRepository",
                "rows endpoint failed for config '$config': ${error.localizedMessage}. Trying first-rows fallback."
            )
            fetchFirstRowsForConfig(config = config, targetRows = targetRows)
        }
    }

    private fun fetchRowsForConfig(config: String, targetRows: Int): List<GaiaProblem> {
        val out = mutableListOf<GaiaProblem>()
        var offset = 0
        val pageSize = 20

        while (out.size < targetRows) {
            val remaining = targetRows - out.size
            val length = minOf(pageSize, remaining)
            val url = buildRowsUrl(config, offset, length)
            val response = readTextFromUrl(url)
            val root = gson.fromJson(response, JsonObject::class.java)
            val rowsArray = root["rows"]?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

            if (rowsArray.isEmpty) {
                break
            }

            rowsArray.forEach { rowItem ->
                val rowObject = rowItem.asJsonObject["row"]?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@forEach
                val extracted = extractProblem(rowObject, index = offset + out.size)
                if (extracted != null) {
                    out.add(extracted)
                }
            }

            if (rowsArray.size() < length) {
                break
            }
            offset += rowsArray.size()
        }

        return out
    }

    private fun fetchFirstRowsForConfig(config: String, targetRows: Int): List<GaiaProblem> {
        val url = buildFirstRowsUrl(config = config, length = targetRows)
        val response = readTextFromUrl(url)
        val root = gson.fromJson(response, JsonObject::class.java)
        val rowsArray = root["rows"]?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

        if (rowsArray.isEmpty) return emptyList()

        val out = mutableListOf<GaiaProblem>()
        rowsArray.forEachIndexed { index, rowItem ->
            val rowObject = rowItem.asJsonObject["row"]?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@forEachIndexed
            val extracted = extractProblem(rowObject, index = index)
            if (extracted != null) {
                out.add(extracted)
            }
        }
        return out.take(targetRows)
    }

    private fun buildRowsUrl(config: String, offset: Int, length: Int): String {
        val encodedDataset = URLEncoder.encode(gaiaRepoId, Charsets.UTF_8.name())
        val encodedConfig = URLEncoder.encode(config, Charsets.UTF_8.name())
        return "https://datasets-server.huggingface.co/rows" +
            "?dataset=$encodedDataset" +
            "&config=$encodedConfig" +
            "&split=validation" +
            "&offset=$offset" +
            "&length=$length"
    }

    private fun buildFirstRowsUrl(config: String, length: Int): String {
        val encodedDataset = URLEncoder.encode(gaiaRepoId, Charsets.UTF_8.name())
        val encodedConfig = URLEncoder.encode(config, Charsets.UTF_8.name())
        return "https://datasets-server.huggingface.co/first-rows" +
            "?dataset=$encodedDataset" +
            "&config=$encodedConfig" +
            "&split=validation" +
            "&length=$length"
    }

    private fun extractProblem(json: JsonObject, index: Int): GaiaProblem? {
        val id = extractString(json, listOf("task_id", "id", "problem_id")) ?: "gaia-$index"
        val question = extractString(
            json,
            listOf("question", "query", "prompt", "instruction", "problem")
        )
        val answer = extractString(
            json,
            listOf("final_answer", "answer", "gold_answer", "expected_answer", "target")
        )

        if (question.isNullOrBlank() || answer.isNullOrBlank()) {
            return null
        }

        return GaiaProblem(
            id = id,
            question = question,
            expectedAnswer = answer
        )
    }

    private fun extractString(json: JsonObject, keys: List<String>): String? {
        keys.forEach { key ->
            val value = json[key] ?: return@forEach
            val parsed = jsonElementToString(value)
            if (!parsed.isNullOrBlank()) {
                return parsed
            }
        }
        return null
    }

    private fun jsonElementToString(element: JsonElement): String? {
        if (element.isJsonNull) return null
        if (element.isJsonPrimitive) {
            return element.asJsonPrimitive.asString
        }
        return element.toString()
    }

    private fun readTextFromUrl(urlString: String): String {
        val maxAttempts = 5
        var lastError: Exception? = null

        repeat(maxAttempts) { attempt ->
            val currentAttempt = attempt + 1
            try {
                val result = executeHttpGet(urlString)
                if (result.statusCode in 200..299) {
                    return result.body
                }

                if (result.statusCode == 401 || result.statusCode == 403 || result.body.contains("gated", ignoreCase = true)) {
                    throw IllegalStateException(
                        "GAIA access denied (HTTP ${result.statusCode}). Accept dataset terms on Hugging Face and set HF_TOKEN."
                    )
                }

                if (isRetryableStatus(result.statusCode) && currentAttempt < maxAttempts) {
                    val delayMs = (currentAttempt * 1200L)
                    Thread.sleep(delayMs)
                    return@repeat
                }

                throw IllegalStateException("GAIA request failed (HTTP ${result.statusCode}): ${result.body}")
            } catch (e: IllegalStateException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (currentAttempt < maxAttempts) {
                    val delayMs = (currentAttempt * 1200L)
                    Thread.sleep(delayMs)
                    return@repeat
                }
            }
        }

        throw lastError ?: IllegalStateException("GAIA request failed after retry attempts.")
    }

    private fun executeHttpGet(urlString: String): HttpResult {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OfflineGPT-Android/1.0")
            if (hfToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $hfToken")
            }
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResult(statusCode = status, body = body)
    }

    private fun isRetryableStatus(statusCode: Int): Boolean {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504
    }
}
