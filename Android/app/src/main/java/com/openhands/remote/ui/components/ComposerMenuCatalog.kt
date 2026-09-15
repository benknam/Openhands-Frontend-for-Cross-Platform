package com.openhands.remote.ui.components

import com.openhands.remote.core.model.PluginSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ComposerMenuCatalog {
    const val SWITCH_AGENT_PROFILE = "切换代理配置文件"
    const val MANAGE_AGENT_PROFILES = "管理代理配置文件"
    const val AVAILABLE_PROFILES = "可用配置文件"
    const val MACROS = "宏"
    const val SHOW_SKILLS = "显示可用技能"
    const val SHOW_HOOKS = "显示可用钩子"
    const val ADD_FILES = "添加文件和图片"
    const val INCREASE_TEST_COVERAGE = "提高测试覆盖率"
    const val FIX_README = "修复 README"
    const val AUTO_MERGE_PRS = "自动合并 PR"
    const val CLEAN_DEPENDENCIES = "清理依赖项"

    fun mainItems(showAgentProfileSwitch: Boolean, showHooks: Boolean = false): List<String> = buildList {
        if (showAgentProfileSwitch) add(SWITCH_AGENT_PROFILE)
        add(MACROS)
        add("---")
        add(SHOW_SKILLS)
        if (showHooks) add(SHOW_HOOKS)
        add("---")
        add(ADD_FILES)
    }

    fun macroItems(): List<String> = listOf(
        INCREASE_TEST_COVERAGE,
        FIX_README,
        AUTO_MERGE_PRS,
        CLEAN_DEPENDENCIES,
    )

    fun macroPrompt(label: String): String? = when (label) {
        INCREASE_TEST_COVERAGE -> INCREASE_TEST_COVERAGE_PROMPT
        FIX_README -> FIX_README_PROMPT
        AUTO_MERGE_PRS -> AUTO_MERGE_PRS_PROMPT
        CLEAN_DEPENDENCIES -> CLEAN_DEPENDENCIES_PROMPT
        else -> null
    }

    fun pluginFromJson(item: JsonObject): PluginSpec? {
        val name = item.string("name") ?: item.string("id") ?: item.string("title")
        val source = item.string("source") ?: item.string("url") ?: item.string("path") ?: name ?: return null
        return PluginSpec(
            source = source,
            ref = item.string("ref") ?: item.string("branch"),
            repoPath = item.string("repo_path") ?: item.string("repoPath"),
            name = name ?: source.substringAfterLast('/'),
            description = item.string("description"),
        )
    }

    fun pluginMatchesSearch(plugin: PluginSpec, query: String): Boolean {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return true
        return listOfNotNull(plugin.name, plugin.description, plugin.source, plugin.repoPath, plugin.ref)
            .any { it.lowercase().contains(trimmed) }
    }

    fun skillName(item: JsonObject): String =
        item.string("name") ?: item.string("id") ?: item.string("title") ?: "未命名技能"

    fun skillTypeLabel(item: JsonObject): String = when (item.string("type")?.lowercase()) {
        "knowledge" -> "Knowledge"
        "repo", "repository" -> "Repository"
        "agentskills", "agent" -> "AgentSkills"
        else -> item.string("type")?.replaceFirstChar { it.uppercase() } ?: "Skill"
    }

    fun skillTriggers(item: JsonObject): List<String> {
        val raw = item["triggers"] ?: item["trigger"]
        return when (raw) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> raw.contentOrNull?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
            else -> emptyList()
        }
    }

    fun skillContent(item: JsonObject): String =
        item.string("content") ?: item.string("body") ?: item.string("prompt").orEmpty()

    fun skillScope(item: JsonObject, projectDir: String?): SkillScope {
        val source = (item.string("source") ?: item.string("path") ?: item.string("file")).orEmpty()
        val type = item.string("type").orEmpty()
        val normalized = source.replace('\\', '/')
        val lower = normalized.lowercase()
        if (lower == "public" || lower.contains("public-skills") || lower.contains("/.openhands/cache/skills/")) {
            return SkillScope.PUBLIC
        }
        if (lower == "user" || lower == "global") return SkillScope.PERSONAL
        if (lower == "project" || lower == "repo" || lower == "sandbox") return SkillScope.PROJECT
        if (isUserHomeSkillPath(normalized)) return SkillScope.PERSONAL
        if (hasSkillDirMarker(normalized)) {
            val projectNorm = projectDir?.replace('\\', '/')?.trimEnd('/')
            if (projectNorm.isNullOrBlank() || normalized.startsWith(projectNorm)) return SkillScope.PROJECT
            return SkillScope.PROJECT
        }
        return if (type == "repo") SkillScope.PROJECT else SkillScope.PUBLIC
    }

    fun groupSkills(skills: List<JsonObject>, projectDir: String?): Map<SkillScope, List<JsonObject>> {
        val grouped = SkillScope.entries.associateWith { mutableListOf<JsonObject>() }
        skills.forEach { skill -> grouped.getValue(skillScope(skill, projectDir)).add(skill) }
        return SkillScope.entries.associateWith { scope ->
            grouped.getValue(scope).sortedBy { skillName(it).lowercase() }
        }
    }

    private fun isUserHomeSkillPath(path: String): Boolean {
        if (Regex("^/Users/[^/]+/\\.(agents|openhands)/").containsMatchIn(path)) return true
        if (Regex("^/home/[^/]+/\\.(agents|openhands)/").containsMatchIn(path)) return true
        return USER_SKILL_DIR_MARKERS.any { marker ->
            val index = path.indexOf(marker)
            if (index == -1) return@any false
            val prefix = path.substring(0, index)
            Regex("^/Users/[^/]+$").matches(prefix) || Regex("^/home/[^/]+$").matches(prefix)
        }
    }

    private fun hasSkillDirMarker(path: String): Boolean =
        USER_SKILL_DIR_MARKERS.any { path.contains(it) }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private val USER_SKILL_DIR_MARKERS = listOf(
        "/.agents/skills/",
        "/.openhands/skills/",
        "/.openhands/microagents/",
    )

    private const val INCREASE_TEST_COVERAGE_PROMPT =
        "I want to increase the test coverage of the repository in the current directory.\n\n" +
            "Please investigate the repo to figure out what language is being used, and where tests are located, if there are any.\n\n" +
            "If there are no tests already in the repo, add a very basic test, using typical testing strategies for the language involved.\n\n" +
            "If there are existing tests, find a function or method which lacks adequate unit tests, and add unit tests for it. Be sure to respect the projects existing test structures.\n\n" +
            "Make sure the tests pass before you finish."

    private const val AUTO_MERGE_PRS_PROMPT =
        "Please add a GitHub action to this repository which automatically merges pull requests from Dependabot so long as the tests are passing."

    private const val FIX_README_PROMPT =
        "Please look at the README and make the following improvements, if they make sense:\n" +
            "* correct any typos that you find\n" +
            "* add missing language annotations on codeblocks\n" +
            "* if there are references to other files or other sections of the README, turn them into links\n" +
            "* make sure the readme has an h1 title towards the top\n" +
            "* make sure any existing sections in the readme are appropriately separated with headings\n\n" +
            "If there are no obvious ways to improve the README, make at least one small change to make the wording clearer or friendlier"

    private const val CLEAN_DEPENDENCIES_PROMPT =
        "Examine the dependencies of the current codebase. Make sure you can run the code and any tests.\n\n" +
            "Then run any commands necessary to update all dependencies to the latest versions, and make sure the code continues to run correctly and the tests pass. If changes need to be made to the codebase, go ahead and make those changes. You can look up documentation for new versions using the browser if you need to.\n\n" +
            "If a particular dependency update is causing trouble (e.g. breaking changes that you can't fix), you can revert it and send a message to the user explaining why.\n\n" +
            "Additionally, if you're able to prune any dependencies that are obviously unused, please do so. You may use third party tools to check for unused dependencies."
}

enum class SkillScope(val title: String) {
    PROJECT("项目技能"),
    PERSONAL("用户技能"),
    PUBLIC("公共技能"),
}
