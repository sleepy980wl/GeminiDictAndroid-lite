package com.geminidict.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiTranslationResult(
    val sourceText: String,
    val detectedLanguage: String,
    val translation: String,
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val detailedExplanation: String = "",
    val examples: List<GeminiExample> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val modelUsed: String = ""
)

data class GeminiExample(
    val english: String,
    val chinese: String
)

/**
 * REST client for Google Gemini Developer API (generateContent).
 * Supports configurable models, custom proxy Base URLs, resilient JSON parsing,
 * HTTP 429/503 exponential backoff retry, and connection testing.
 */
class GeminiTranslationService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    suspend fun testConnection(
        apiKey: String,
        model: String,
        baseUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("请先填写 Gemini API Key。"))
        }
        val res = translate("hello", apiKey, model, baseUrl)
        res.map { "连接成功！模型 ${it.modelUsed} 响应正常（示例译文：${it.translation.take(30)}）" }
    }

    suspend fun translate(
        query: String,
        apiKey: String,
        model: String,
        baseUrl: String
    ): Result<GeminiTranslationResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("尚未配置 Gemini API Key，请先在「设置」页面填写。")
            )
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("请输入要翻译的单词或句子。"))
        }

        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanModel = model.trim().removePrefix("models/")
        val url = "$cleanBase/v1beta/models/$cleanModel:generateContent"

        val prompt = buildString {
            appendLine("You are an expert English-Chinese bilingual dictionary and translator.")
            appendLine("Translate the following input between English and Simplified Chinese.")
            appendLine("Return ONLY a valid JSON object (no markdown fences) with this exact schema:")
            appendLine(
                """
                {
                  "detectedLanguage": "en or zh",
                  "translation": "concise primary translation",
                  "phonetic": "IPA phonetic if English word/phrase, else pinyin",
                  "partOfSpeech": "e.g. n. / v. / adj. / sentence",
                  "detailedExplanation": "clear usage notes, nuances, or grammar breakdown in Chinese",
                  "examples": [
                    {"english": "natural English sentence", "chinese": "自然流畅的中文翻译"},
                    {"english": "another English sentence", "chinese": "另一条中文翻译"}
                  ],
                  "synonyms": ["synonym1", "synonym2"]
                }
                """.trimIndent()
            )
            appendLine("Input: $trimmed")
        }

        val payload = JsonObject().apply {
            val part = JsonObject().apply { addProperty("text", prompt) }
            val content = JsonObject().apply {
                addProperty("role", "user")
                add("parts", gson.toJsonTree(listOf(part)))
            }
            add("contents", gson.toJsonTree(listOf(content)))
            val genConfig = JsonObject().apply {
                addProperty("temperature", 0.2)
                addProperty("responseMimeType", "application/json")
            }
            add("generationConfig", genConfig)
        }

        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey.trim())
            .header("Content-Type", "application/json; charset=utf-8")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var lastError: Exception? = null
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (response.code == 429 || response.code == 503) {
                        val errMsg = extractErrorMessage(rawBody) ?: "HTTP ${response.code}"
                        lastError = IOException("Gemini 服务限流或繁忙 (${response.code}): $errMsg")
                        if (attempt < maxAttempts) {
                            delay(attempt * 1500L)
                            return@use
                        }
                        return@withContext Result.failure(lastError!!)
                    }
                    if (!response.isSuccessful) {
                        val errorMsg = extractErrorMessage(rawBody) ?: "HTTP ${response.code}"
                        return@withContext Result.failure(
                            IOException("Gemini API 请求失败 (${response.code}): $errorMsg")
                        )
                    }

                    val root = gson.fromJson(rawBody, JsonObject::class.java)
                    val text = root
                        ?.getAsJsonArray("candidates")
                        ?.firstOrNull()
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.getAsJsonObject("content")
                        ?.getAsJsonArray("parts")
                        ?.firstOrNull()
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.get("text")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?: return@withContext Result.failure(IOException("Gemini 返回内容为空。"))

                    val cleanedJson = stripMarkdownFences(text)
                    val parsed = parseStructuredResult(trimmed, cleanedJson, cleanModel)
                    return@withContext parsed
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts) {
                    delay(attempt * 1000L)
                }
            }
        }
        Result.failure(IOException("网络或解析异常: ${lastError?.message}", lastError))
    }

    private fun extractErrorMessage(rawBody: String): String? {
        return try {
            val obj = gson.fromJson(rawBody, JsonObject::class.java)
            obj?.getAsJsonObject("error")?.get("message")?.asString
        } catch (_: Exception) {
            rawBody.take(200).ifBlank { null }
        }
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("```")) {
            trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            trimmed
        }
    }

    internal fun parseStructuredResult(
        sourceText: String,
        jsonText: String,
        modelUsed: String
    ): Result<GeminiTranslationResult> {
        return try {
            val elem = gson.fromJson(jsonText, com.google.gson.JsonElement::class.java)
            if (elem == null || !elem.isJsonObject) {
                if (jsonText.isNotBlank() && !jsonText.trimStart().startsWith("{")) {
                    return Result.success(
                        GeminiTranslationResult(
                            sourceText = sourceText,
                            detectedLanguage = "auto",
                            translation = jsonText.trim(),
                            modelUsed = modelUsed
                        )
                    )
                }
                return Result.failure(IOException("Gemini 返回的 JSON 格式不完整，请重试。"))
            }
            val obj = elem.asJsonObject
            val translation = obj.safeString("translation")
            val explanation = obj.safeString("detailedExplanation")
            if (translation.isBlank() && explanation.isBlank()) {
                return Result.failure(IOException("Gemini 未返回有效译文内容，请重试。"))
            }

            val examplesList = mutableListOf<GeminiExample>()
            val exArray = obj.get("examples")?.takeIf { it.isJsonArray }?.asJsonArray
            exArray?.forEach { item ->
                if (item.isJsonObject) {
                    val exObj = item.asJsonObject
                    val en = exObj.safeString("english")
                    val zh = exObj.safeString("chinese")
                    if (en.isNotBlank()) {
                        examplesList.add(GeminiExample(english = en, chinese = zh))
                    }
                }
            }

            val synonymsList = mutableListOf<String>()
            val synArray = obj.get("synonyms")?.takeIf { it.isJsonArray }?.asJsonArray
            synArray?.forEach { item ->
                if (item.isJsonPrimitive) {
                    val s = item.asString.trim()
                    if (s.isNotEmpty()) synonymsList.add(s)
                }
            }

            Result.success(
                GeminiTranslationResult(
                    sourceText = sourceText,
                    detectedLanguage = obj.safeString("detectedLanguage"),
                    translation = translation.ifBlank { explanation },
                    phonetic = obj.safeString("phonetic"),
                    partOfSpeech = obj.safeString("partOfSpeech"),
                    detailedExplanation = explanation,
                    examples = examplesList,
                    synonyms = synonymsList,
                    modelUsed = modelUsed
                )
            )
        } catch (_: Exception) {
            if (jsonText.isNotBlank() && !jsonText.trimStart().startsWith("{")) {
                Result.success(
                    GeminiTranslationResult(
                        sourceText = sourceText,
                        detectedLanguage = "auto",
                        translation = jsonText.trim(),
                        modelUsed = modelUsed
                    )
                )
            } else {
                Result.failure(IOException("Gemini 响应解析失败，请稍后重试。"))
            }
        }
    }

    private fun JsonObject.safeString(key: String): String {
        val v = this.get(key) ?: return ""
        return if (v.isJsonPrimitive) v.asString.trim() else ""
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
