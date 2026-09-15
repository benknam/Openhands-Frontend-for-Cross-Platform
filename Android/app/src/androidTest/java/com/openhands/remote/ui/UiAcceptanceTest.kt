package com.openhands.remote.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.BackendProfile
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.model.ConversationSummary
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.ui.components.CollapsedOpenHandsSidebar
import com.openhands.remote.ui.components.ConversationCenterPane
import com.openhands.remote.ui.components.MessageComposer
import com.openhands.remote.ui.components.OpenHandsSidebar
import com.openhands.remote.ui.components.WorkspaceInspectorPanel
import com.openhands.remote.ui.theme.OpenHandsRemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiAcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sidebar_collapses_and_expands() {
        composeRule.setContent {
            OpenHandsRemoteTheme {
                var collapsed by remember { mutableStateOf(false) }
                if (collapsed) {
                    CollapsedOpenHandsSidebar(
                        onExpand = { collapsed = false },
                        onCreate = {},
                        onSettings = {},
                    )
                } else {
                    OpenHandsSidebar(
                        conversations = listOf(ConversationSummary("conversation-1")),
                        selectedId = null,
                        filter = "",
                        loading = false,
                        onFilterChange = {},
                        logoCollapseEnabled = true,
                        onSelect = {},
                        onCreate = {},
                        onRefresh = {},
                        onSettings = {},
                        onExtensions = {},
                        onBackend = {},
                        onManageBackend = {},
                        onAddBackend = {},
                        onAddWorkspace = {},
                        onManageWorkspace = {},
                        selectedWorkspace = null,
                        onWorkspaceSelected = {},
                        backendLabel = "测试后端",
                        onAutomate = {},
                        onSort = {},
                        connectionState = ConnectionState.CONNECTED,
                        sortMode = "updated",
                        onDeleteAll = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Logo 图标收拢展开").assertExists().performClick()
        composeRule.onNodeWithText("搜索命令").assertDoesNotExist()
        composeRule.onNodeWithText("新建对话").assertDoesNotExist()
        composeRule.onNodeWithText("Automate").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Logo 图标收拢展开").performClick()
        composeRule.onNodeWithText("搜索命令").assertExists()
        composeRule.onNodeWithText("新建对话").assertExists()
    }

    @Test
    fun inspector_tabs_switch_content() {
        composeRule.setContent {
            OpenHandsRemoteTheme {
                WorkspaceInspectorPanel(
                    connection = BackendConnection(
                        BackendProfile("test", "测试后端", "http://localhost:8000"),
                    ),
                    connectionState = ConnectionState.CONNECTED,
                    eventCount = 3,
                    selectedConversationId = "conversation-1",
                )
            }
        }

        composeRule.onNodeWithText("文件树").assertExists()
        composeRule.onNodeWithContentDescription("工作区页签：终端").performClick()
        composeRule.onNodeWithContentDescription("工作区页签：终端").assertExists()
        composeRule.onNodeWithText("执行命令").assertExists()
        composeRule.onNodeWithText("文件树").assertDoesNotExist()
    }

    @Test
    fun composer_ime_send_invokes_callback() {
        var sent = ""
        composeRule.setContent {
            OpenHandsRemoteTheme {
                MessageComposer(
                    value = "",
                    enabled = true,
                    sending = false,
                    onValueChange = {},
                    onSend = { sent = "sent" },
                )
            }
        }

        val input = composeRule.onNodeWithContentDescription("消息输入框")
        input.performTextInput("hello")
        input.performImeAction()
        composeRule.runOnIdle { check(sent == "sent") }
    }

    @Test
    fun composer_locks_send_while_sending() {
        composeRule.setContent {
            OpenHandsRemoteTheme {
                MessageComposer(
                    value = "hello",
                    enabled = true,
                    sending = true,
                    onValueChange = {},
                    onSend = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("消息输入框").assertIsNotEnabled()
        composeRule.onNodeWithText("发送").assertDoesNotExist()
    }

    @Test
    fun center_displays_send_failure() {
        composeRule.setContent {
            OpenHandsRemoteTheme {
                ConversationCenterPane(
                    events = emptyList(),
                    connection = BackendConnection(
                        BackendProfile("test", "测试后端", "http://localhost:8000"),
                    ),
                    httpClient = AgentServerHttpClient(),
                    connectionState = ConnectionState.CONNECTED,
                    lastEventTimestamp = null,
                    conversations = emptyList(),
                    activeConversationId = "conversation-1",
                    actionMessage = "发送失败：网络不可用",
                    sendingMessage = false,
                    deletingConversation = false,
                    chatBubbleActionsEnabled = false,
                    adaptiveWidthEnabled = false,
                    composerHidingEnabled = false,
                    autoHideComposer = false,
                    autoHideDelaySeconds = 5,
                    onReconnect = {},
                    onDelete = {},
                    onPause = {},
                    onResume = {},
                    onInterrupt = {},
                    onSend = {},
                    onOpenWorkspace = {},
                    onPlugins = {},
                )
            }
        }

        composeRule.onNodeWithText("发送失败：网络不可用").assertExists()
    }
}
