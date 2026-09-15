package com.openhands.remote.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.ui.theme.OpenHandsShapes

@Composable
fun MarkdownContent(content: String) {
    val lines = content.take(MAX_MARKDOWN_CHARS).lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var codeLanguage: String? = null
    val codeLines = mutableListOf<String>()

    fun flushCode() {
        if (codeLanguage != null) {
            blocks += MarkdownBlock.Code(codeLanguage, codeLines.joinToString("\n"))
            codeLanguage = null
            codeLines.clear()
        }
    }

    lines.forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (codeLanguage == null) {
                codeLanguage = line.trim().removePrefix("```").ifBlank { null }
            } else {
                flushCode()
            }
        } else if (codeLanguage != null) {
            codeLines += line
        } else {
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> blocks += MarkdownBlock.Spacer
                trimmed.startsWith("### ") -> blocks += MarkdownBlock.Heading(3, trimmed.removePrefix("### "))
                trimmed.startsWith("## ") -> blocks += MarkdownBlock.Heading(2, trimmed.removePrefix("## "))
                trimmed.startsWith("# ") -> blocks += MarkdownBlock.Heading(1, trimmed.removePrefix("# "))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    blocks += MarkdownBlock.Bullet(trimmed.drop(2))
                else -> blocks += MarkdownBlock.Paragraph(trimmed)
            }
        }
    }
    flushCode()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                MarkdownBlock.Spacer -> Text("", modifier = Modifier.padding(2.dp))
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                is MarkdownBlock.Paragraph -> MarkdownParagraph(block.text)
                is MarkdownBlock.Bullet -> MarkdownParagraph("• ${block.text}")
                is MarkdownBlock.Code -> CodeBlock(block.language, block.code)
            }
        }
    }
}


@Composable
private fun MarkdownParagraph(value: String) {
    val uriHandler = LocalUriHandler.current
    val annotated = inlineMarkdown(
        value = value,
        linkColor = MaterialTheme.colorScheme.primary,
        onLinkClick = uriHandler::openUri,
    )
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun CodeBlock(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val boundedCode = code.take(MAX_CODE_CHARS)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = OpenHandsShapes.codeBlock,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = language?.takeIf { it.isNotBlank() } ?: "代码",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(boundedCode))
                copied = true
            }) {
                Text(if (copied) "已复制" else "复制")
            }
        }
        Text(
            text = syntaxHighlightedCode(boundedCode, language),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

private fun inlineMarkdown(
    value: String,
    linkColor: Color = Color(0xFF8AB4F8),
    onLinkClick: (String) -> Unit = {},
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    val token = Regex("(\\[[^]]+\\]\\(https?://[^)]+\\))|(\\*\\*.+?\\*\\*)|(`.+?`)|(\\*.+?\\*)")
    token.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val raw = match.value
        when {
            raw.startsWith("[") -> {
                val link = Regex("\\[([^]]+)\\]\\((https?://[^)]+)\\)").matchEntire(raw)
                if (link == null) append(raw) else {
                    val url = link.groupValues[2]
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            linkInteractionListener = { _ -> onLinkClick(url) },
                        ),
                    ) {
                        withStyle(SpanStyle(color = linkColor)) {
                            append(link.groupValues[1])
                        }
                    }
                }
            }
            raw.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(raw.removeSurrounding("**"))
            }
            raw.startsWith("`") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color.LightGray.copy(alpha = 0.25f),
                ),
            ) { append(raw.removeSurrounding("`")) }
            else -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(raw.removeSurrounding("*"))
            }
        }
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}


private fun syntaxHighlightedCode(code: String, language: String?): AnnotatedString = buildAnnotatedString {
    val normalized = language.orEmpty().lowercase()
    val diffMode = normalized == "diff" || normalized == "patch"
    code.lines().forEachIndexed { index, line ->
        if (diffMode) {
            val lineColor = when {
                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF8BE28B)
                line.startsWith("-") && !line.startsWith("---") -> Color(0xFFFF8A8A)
                line.startsWith("@@") -> Color(0xFF8AB4F8)
                else -> Color.Unspecified
            }
            if (lineColor != Color.Unspecified) withStyle(SpanStyle(color = lineColor)) { append(line) }
            else append(line)
        } else {
            appendHighlightedTokens(line, normalized)
        }
        if (index < code.lines().lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendHighlightedTokens(line: String, language: String) {
    val token = Regex("(//.*$|#.*$)|([\\\"'](?:\\\\.|[^\\\"'])*[\\\"'])|\\b(?:fun|val|var|class|interface|if|else|for|while|return|import|from|def|const|let|function|true|false|null|async|await)\\b")
    var cursor = 0
    token.findAll(line).forEach { match ->
        append(line.substring(cursor, match.range.first))
        val color = when {
            match.value.startsWith("//") || match.value.startsWith("#") -> Color(0xFF7F9F7F)
            match.value.startsWith("\"") || match.value.startsWith("'") -> Color(0xFFE6C07B)
            else -> Color(0xFFC792EA)
        }
        withStyle(SpanStyle(color = color)) { append(match.value) }
        cursor = match.range.last + 1
    }
    append(line.substring(cursor))
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data object Spacer : MarkdownBlock
}

private const val MAX_MARKDOWN_CHARS = 24000
private const val MAX_CODE_CHARS = 16000
