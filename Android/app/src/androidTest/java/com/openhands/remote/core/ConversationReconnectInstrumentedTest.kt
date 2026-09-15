package com.openhands.remote.core

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openhands.remote.core.model.AuthMode
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.BackendProfile
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.network.DefaultConversationSyncManager
import com.openhands.remote.core.network.OkHttpConversationSocket
import com.openhands.remote.core.network.SqliteEventStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationReconnectInstrumentedTest {
    private var manager: DefaultConversationSyncManager? = null

    @After
    fun tearDown() {
        manager?.stop()
    }

    @Test
    fun realConversationSocket_recoversAfterNetworkStateTransition() {
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            val baseUrl = arguments.getString("backendUrl") ?: "http://192.168.2.99:8000"
            val conversationId = arguments.getString("conversationId")
                ?: "bfe08168-8aa1-40c3-b87b-0f3ddbdbd8d9"
            val connection = BackendConnection(
                BackendProfile(
                    id = baseUrl,
                    name = "instrumented backend",
                    baseUrl = baseUrl,
                    authMode = AuthMode.SESSION_API_KEY,
                ),
                apiKey = arguments.getString("sessionApiKey"),
            )
            val currentManager = DefaultConversationSyncManager(
                socket = OkHttpConversationSocket(),
                eventStore = SqliteEventStore(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                ),
            )
            manager = currentManager

            currentManager.start(connection, conversationId)
            awaitConnectedOrSyncing(currentManager)

            val physicalNetworkToggle = arguments.getString("physicalNetworkToggle") == "true"
            if (physicalNetworkToggle) {
                val connectivityManager = InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onLost(network: android.net.Network) {
                        currentManager.networkLost()
                    }

                    override fun onAvailable(network: android.net.Network) {
                        currentManager.networkAvailable()
                    }
                }
                connectivityManager.registerDefaultNetworkCallback(callback)
                try {
                    withTimeout(30_000) {
                        currentManager.state.filter {
                            it.connectionState == ConnectionState.WAITING_NETWORK
                        }.first()
                    }
                    withTimeout(30_000) {
                        currentManager.state.filter {
                            it.connectionState == ConnectionState.SYNCING ||
                                it.connectionState == ConnectionState.CONNECTED
                        }.first()
                    }
                } finally {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            } else {
                currentManager.networkLost()
                assertEquals(
                    ConnectionState.WAITING_NETWORK,
                    currentManager.state.value.connectionState,
                )
                currentManager.networkAvailable()
                awaitConnectedOrSyncing(currentManager)
            }
        }
    }

    private suspend fun awaitConnectedOrSyncing(manager: DefaultConversationSyncManager) {
        withTimeout(15_000) {
            manager.state.filter {
                it.connectionState == ConnectionState.SYNCING ||
                    it.connectionState == ConnectionState.CONNECTED
            }.first()
        }
    }
}
