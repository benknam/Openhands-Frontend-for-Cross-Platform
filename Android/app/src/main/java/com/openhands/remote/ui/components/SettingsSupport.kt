package com.openhands.remote.ui.components

import com.openhands.remote.core.model.AgentProfileSummary
import com.openhands.remote.core.model.LlmProfileSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class SettingsNavItem(
    val key: String,
    val title: String,
    val subtitle: String,
)

data class SettingsField(
    val key: String,
    val label: String,
    val description: String? = null,
    val valueType: String = "string",
    val prominence: String = "minor",
    val secret: Boolean = false,
    val required: Boolean = false,
    val choices: List<Pair<String, String>> = emptyList(),
    val dependsOn: List<String> = emptyList(),
    val defaultValue: JsonElement? = null,
)

data class SettingsSection(
    val key: String,
    val label: String,
    val fields: List<SettingsField>,
)

data class LlmProfileDetail(
    val name: String,
    val config: JsonObject,
    val apiKeySet: Boolean,
)

data class AgentProfileDetail(
    val name: String,
    val profile: JsonObject,
)

object SettingsCatalog {
    val navItems = listOf(
        SettingsNavItem("代理", "代理", "创建和管理可重用的代理设置。"),
        SettingsNavItem("LLM", "LLM", "代理的模型、API 密钥和选项。"),
        SettingsNavItem("压缩器", "压缩器", "对长对话进行摘要，以保持在上下文限制内。"),
        SettingsNavItem("代理上下文", "代理上下文", "让代理保存笔记并在新对话中回忆起来。"),
        SettingsNavItem("验证", "验证", "针对操作的确认提示和安全检查。"),
        SettingsNavItem("应用程序", "应用程序", "语言、主题、通知和 Git 等配置。"),
        SettingsNavItem("使用优化", "使用优化", "针对 Android 客户端的界面与交互优化。"),
        SettingsNavItem("机密", "机密", "安全存储自定义机密，供代理在运行时读取。"),
    )

    val languages = listOf(
        "zh-CN" to "中文（简体）",
        "zh-TW" to "中文（繁体）",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "pt" to "Português",
        "ru" to "Русский",
    )

    private val labels = mapOf(
        "condenser" to ("压缩器" to "对长对话进行摘要，以保持在上下文限制内。"),
        "agent_context" to ("代理上下文" to "让代理保存笔记并在新对话中回忆起来。"),
        "verification" to ("验证" to "针对操作的确认提示和安全检查。"),
        "general" to ("常规" to ""),
        "agent" to ("代理" to "要使用的代理类。"),
        "enable_sub_agents" to ("启用子智能体" to "通过 TaskToolSet 启用子智能体委派。"),
        "enable_switch_llm_tool" to ("允许智能体切换 LLM 配置" to "允许智能体自行将当前对话切换到另一个已保存的 LLM 配置。禁用后将移除该工具。"),
        "tool_concurrency_limit" to ("工具并发限制" to "每个代理步骤可并发执行的最大工具调用数。"),
        "llm.model" to ("模型" to "模型名称。"),
        "llm.api_key" to ("API密钥" to "用于与LLM提供商进行身份验证的API密钥。"),
        "llm.base_url" to ("基础URL" to "自定义基础URL。"),
        "llm.temperature" to ("温度" to "控制模型响应的随机性。较低的值更具确定性。"),
        "llm.top_p" to ("Top P" to "通过将令牌选择限制为累积概率超过此值的最小集合来控制核采样。"),
        "llm.top_k" to ("Top K" to "将令牌选择限制为最可能的K个令牌。"),
        "llm.max_output_tokens" to ("最大输出令牌" to "模型可能生成的最大输出令牌数。"),
        "llm.max_input_tokens" to ("最大输入令牌" to "最大输入令牌数。"),
        "llm.timeout" to ("超时" to "HTTP超时时间（秒）。"),
        "llm.num_retries" to ("重试次数" to "失败请求的重试次数。"),
        "llm.stream" to ("流式传输" to "启用来自LLM的流式响应。"),
        "llm.native_tool_calling" to ("原生工具调用" to "使用原生工具调用。"),
        "llm.caching_prompt" to ("缓存提示" to "启用提示缓存。"),
        "llm.disable_vision" to ("禁用视觉" to "禁用支持视觉的模型的图像处理。"),
        "llm.reasoning_effort" to ("推理力度" to "推理投入的努力程度（低、中、高、超高或无）。"),
        "llm.reasoning_summary" to ("推理摘要" to "推理摘要的详细程度（自动、简洁或详细）。"),
        "agent_context.load_memory" to ("持久代理记忆" to "代理会在 .openhands/memory/ 下保存笔记，并在每次新会话开始时加载它们，随着时间推移学习您的代码库和偏好。"),
        "condenser.enabled" to ("启用内存压缩" to "启用LLM摘要压缩器。"),
        "condenser.max_size" to ("最大大小" to "压缩器运行前保留的最大事件数。"),
        "condenser.condenser_kind" to ("压缩器类型" to "选择摘要压缩器或关闭压缩。"),
        "condenser.max_tokens" to ("最大令牌" to "压缩器运行前允许的最大令牌数。未设置时仅按事件数触发。"),
        "condenser.keep_first" to ("保留开头事件" to "压缩前至少保留的初始事件数。"),
        "condenser.minimum_progress" to ("最小进度" to "压缩被视为成功所需的最小事件比例。"),
        "condenser.hard_context_reset_max_retries" to ("硬重置重试" to "硬上下文重置失败后的重试次数。"),
        "condenser.hard_context_reset_context_scaling" to ("硬重置缩放" to "硬重置失败后缩小事件字符串的比例。"),
        "verification.critic_enabled" to ("启用批评" to "为代理启用批评评估。"),
        "verification.critic_mode" to ("批评模式" to "批评评估应该何时运行。"),
        "verification.enable_iterative_refinement" to ("启用迭代改进" to "当批评分数低于阈值时自动重试任务。"),
        "verification.critic_threshold" to ("批评阈值" to "用于迭代改进的批评成功阈值。"),
        "verification.max_refinement_iterations" to ("最大改进迭代次数" to "批评反馈后的最大改进尝试次数。"),
        "verification.critic_server_url" to ("批评服务器URL" to "覆盖批评服务URL。"),
        "verification.critic_model_name" to ("批评模型名称" to "覆盖批评模型名称。"),
        "verification.critic_api_key" to ("批评器 API 密钥" to "批评器调用其 LLM 所使用的可选 API 密钥。留空时，批评器会复用 LLM API 密钥。"),
        "confirmation_mode" to ("确认模式" to "在执行有风险的操作之前要求用户确认。"),
        "security_analyzer" to ("安全分析器" to "在执行前评估操作的安全分析器。"),
        "max_iterations" to ("最大迭代次数" to "会话在停止前将运行的最大迭代次数。"),
    )

    private val choiceLabels = mapOf(
        "llm_summarizing" to "LLM 摘要",
        "no_op" to "无操作",
        "finish_and_message" to "完成和消息",
        "all_actions" to "所有操作",
        "llm" to "LLM",
        "none" to "无",
        "low" to "低",
        "medium" to "中",
        "high" to "高",
        "xhigh" to "超高",
        "auto" to "自动",
        "concise" to "简洁",
        "detailed" to "详细",
    )

    fun labelFor(key: String, fallback: String): String = labels[key]?.first ?: fallback.ifBlank { key }

    fun descriptionFor(key: String, fallback: String?): String? =
        labels[key]?.second ?: fallback?.takeIf { it.isNotBlank() }

    fun choiceLabel(value: String, fallback: String = value): String = choiceLabels[value] ?: fallback
}

object SettingsJson {
    private val profileNameRegex = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

    fun lookup(root: JsonObject?, path: String): JsonElement? {
        if (root == null || path.isBlank()) return null
        var current: JsonElement? = root
        path.split('.').forEach { part ->
            current = (current as? JsonObject)?.get(part) ?: return null
        }
        return current
    }

    fun stringValue(root: JsonObject?, path: String, fallback: String = ""): String {
        val element = lookup(root, path) ?: return fallback
        return (element as? JsonPrimitive)?.contentOrNull ?: fallback
    }

    fun booleanValue(root: JsonObject?, path: String, fallback: Boolean = false): Boolean {
        val raw = (lookup(root, path) as? JsonPrimitive)?.contentOrNull ?: return fallback
        return raw.toBooleanStrictOrNull() ?: fallback
    }

    fun nestedObject(root: JsonObject?, path: String): JsonObject? = lookup(root, path) as? JsonObject

    fun putPath(path: String, value: JsonElement): JsonObject {
        val parts = path.split('.').filter { it.isNotBlank() }
        var current: JsonElement = value
        for (part in parts.asReversed()) {
            val nested = current
            current = buildJsonObject { put(part, nested) }
        }
        return current as JsonObject
    }

    fun deepMerge(base: JsonObject, overlay: JsonObject): JsonObject = buildJsonObject {
        (base.keys + overlay.keys).forEach { key ->
            val left = base[key]
            val right = overlay[key]
            when {
                left is JsonObject && right is JsonObject -> put(key, deepMerge(left, right))
                right != null -> put(key, right)
                left != null -> put(key, left)
            }
        }
    }

    fun mergeAll(vararg objects: JsonObject): JsonObject =
        objects.fold(buildJsonObject { }) { acc, next -> deepMerge(acc, next) }

    fun withoutKeys(source: JsonObject, vararg keys: String): JsonObject = buildJsonObject {
        source.forEach { (key, value) ->
            if (key !in keys) put(key, value)
        }
    }

    fun textOf(element: JsonElement?): String = when (element) {
        null, JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> element.toString()
    }

    fun parseFieldValue(field: SettingsField, raw: String, original: JsonElement?): JsonElement {
        val trimmed = raw.trim()
        return when (field.valueType) {
            "boolean" -> JsonPrimitive(trimmed.equals("true", ignoreCase = true))
            "integer" -> {
                val number = trimmed.toIntOrNull()
                if (number == null) original ?: JsonNull else JsonPrimitive(number)
            }
            "number" -> {
                val number = trimmed.toDoubleOrNull()
                if (number == null) original ?: JsonNull else JsonPrimitive(number)
            }
            "array", "object" -> {
                if (trimmed.isEmpty()) JsonNull else runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
                }.getOrElse { JsonPrimitive(raw) }
            }
            else -> if (trimmed.isEmpty()) JsonNull else JsonPrimitive(raw)
        }
    }

    fun isProfileNameValid(value: String): Boolean = profileNameRegex.matches(value)

    fun deriveProfileName(model: String): String {
        val modelName = model.substringAfterLast('/').ifBlank { model }
        var sanitized = modelName
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        if (sanitized.isNotEmpty() && !sanitized.first().isLetterOrDigit()) {
            sanitized = "profile-$sanitized"
        }
        if (sanitized.length > 64) sanitized = sanitized.take(64).trimEnd('-')
        return sanitized.ifBlank { "default-profile" }
    }

    fun uniqueCopyName(base: String, existing: Set<String>): String {
        val seed = if (isProfileNameValid("$base-copy")) "$base-copy" else "profile-copy"
        if (seed !in existing) return seed
        var counter = 1
        while ("$seed-$counter" in existing) counter += 1
        return "$seed-$counter"
    }

    fun parseSchema(root: JsonObject?, sectionKeys: Set<String>? = null): List<SettingsSection> {
        val sections = (root?.get("sections") as? JsonArray).orEmpty()
        return sections.mapNotNull { item ->
            val section = item as? JsonObject ?: return@mapNotNull null
            val key = section["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (sectionKeys != null && key !in sectionKeys) return@mapNotNull null
            val fields = ((section["fields"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { fieldItem ->
                val field = fieldItem as? JsonObject ?: return@mapNotNull null
                val fieldKey = field["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val choices = ((field["choices"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { choiceItem ->
                    val choice = choiceItem as? JsonObject ?: return@mapNotNull null
                    val value = textOf(choice["value"]).ifBlank { return@mapNotNull null }
                    value to SettingsCatalog.choiceLabel(value, textOf(choice["label"]).ifBlank { value })
                }
                SettingsField(
                    key = fieldKey,
                    label = SettingsCatalog.labelFor(fieldKey, field["label"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                    description = SettingsCatalog.descriptionFor(
                        fieldKey,
                        field["description"]?.jsonPrimitive?.contentOrNull,
                    ),
                    valueType = field["value_type"]?.jsonPrimitive?.contentOrNull ?: "string",
                    prominence = field["prominence"]?.jsonPrimitive?.contentOrNull ?: "minor",
                    secret = field["secret"]?.jsonPrimitive?.contentOrNull.toBoolean(),
                    required = field["required"]?.jsonPrimitive?.contentOrNull.toBoolean(),
                    choices = choices,
                    dependsOn = ((field["depends_on"] as? JsonArray) ?: JsonArray(emptyList()))
                        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                    defaultValue = field["default"],
                )
            }
            SettingsSection(
                key = key,
                label = SettingsCatalog.labelFor(key, section["label"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                fields = fields,
            )
        }
    }

    fun parseLlmDetail(body: JsonObject): LlmProfileDetail {
        val config = (body["config"] as? JsonObject)
            ?: (body["llm"] as? JsonObject)
            ?: body
        return LlmProfileDetail(
            name = body["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            config = config,
            apiKeySet = body["api_key_set"]?.jsonPrimitive?.contentOrNull.toBoolean(),
        )
    }

    fun parseAgentDetail(body: JsonObject): AgentProfileDetail {
        val profile = (body["profile"] as? JsonObject) ?: body
        return AgentProfileDetail(
            name = body["name"]?.jsonPrimitive?.contentOrNull
                ?: profile["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            profile = profile,
        )
    }

    fun llmDisplayName(profile: LlmProfileSummary): String =
        profile.model?.let { "${profile.name}  ·  $it" } ?: profile.name

    fun agentSecondary(profile: AgentProfileSummary): String =
        if (profile.agentKind == "acp") "ACP" else profile.llmProfileRef.orEmpty()

    private fun String?.toBoolean(): Boolean = this?.toBooleanStrictOrNull() ?: false
}
