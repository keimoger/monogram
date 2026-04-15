package org.monogram.data.di

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.monogram.data.gateway.TdLibException
import org.monogram.data.gateway.isExpectedProxyFailure
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class TdLibClient {
    private val TAG = "TdLibClient"
    private val retryAfterUntilMsByScope = ConcurrentHashMap<String, Long>()
    private val _updates = MutableSharedFlow<TdApi.Update>(
        replay = 3,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated = _isAuthenticated.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    init {
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(0))
            Client.execute(TdApi.SetLogStream(TdApi.LogStreamEmpty()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable TDLib logs", e)
        }
    }

    val updates: SharedFlow<TdApi.Update> = _updates

    private val client = Client.create(
        { result ->
            if (result is TdApi.Update) {
                if (result is TdApi.UpdateAuthorizationState) {
                    val state = result.authorizationState
                    _isInitialized.value = state !is TdApi.AuthorizationStateWaitTdlibParameters
                    _isAuthenticated.value = state is TdApi.AuthorizationStateReady
                }
                _updates.tryEmit(result)
            }
        },
        { error ->
            Log.e(TAG, "Update exception handler", error)
        },
        { error ->
            Log.e(TAG, "Default exception handler", error)
        }
    )

    fun <T : TdApi.Object> send(function: TdApi.Function<T>, callback: (TdApi.Object) -> Unit = {}) {
        client.send(function) { result ->
            if (result is TdApi.Error) {
                if (result.isExpectedProxyFailure()) {
                    Log.w(
                        TAG,
                        "Expected proxy error in send $function: ${result.code} ${result.message}"
                    )
                } else if (result.code != 404) {
                    Log.e(TAG, "Error in send $function: ${result.code} ${result.message}")
                } else {
                    Log.w(TAG, "Not found in send $function: ${result.message}")
                }
            }
            callback(result)
        }
    }

    suspend fun <T : TdApi.Object> sendSuspend(function: TdApi.Function<T>): T {
        if (function !is TdApi.SetTdlibParameters && 
            function !is TdApi.SetLogVerbosityLevel && 
            function !is TdApi.GetOption &&
            function !is TdApi.GetAuthorizationState) {
            if (!_isInitialized.value) {
                Log.d(TAG, "Waiting for TDLib initialization before sending $function")
                isInitialized.first { it }
            }
        }

        var retries = 0
        while (true) {
            waitForRetryWindow(function)
            val result = awaitResult(function)

            if (result !is TdApi.Error) {
                @Suppress("UNCHECKED_CAST")
                return result as T
            }

            if (result.code == 429 && retries < 3) {
                retries++
                val retryAfterMs = parseRetryAfterMs(result.message)
                val retryScope = retryScope(function)
                Log.w(
                    TAG,
                    "Rate limited for $function, scope=$retryScope, retrying in ${retryAfterMs}ms (attempt $retries)"
                )
                updateRetryWindow(retryScope, retryAfterMs)
                continue
            }

            val isExpectedUserFullInfoMiss =
                function is TdApi.GetUserFullInfo &&
                        result.code == 400 &&
                        result.message.contains("user not found", ignoreCase = true)

            if (isExpectedUserFullInfoMiss) {
                Log.w(TAG, "User not found in sendSuspend $function: ${result.code} ${result.message}")
            } else if (result.isExpectedProxyFailure()) {
                Log.w(
                    TAG,
                    "Expected proxy error in sendSuspend $function: ${result.code} ${result.message}"
                )
            } else if (result.code != 404) {
                Log.e(TAG, "Error in sendSuspend $function: ${result.code} ${result.message}")
            } else {
                Log.w(TAG, "Not found in sendSuspend $function: ${result.message}")
            }
            throw TdLibException(result)
        }
    }

    private suspend fun <T : TdApi.Object> awaitResult(function: TdApi.Function<T>): TdApi.Object =
        suspendCancellableCoroutine { cont ->
            client.send(function) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }

    private suspend fun waitForRetryWindow(function: TdApi.Function<*>) {
        val retryScope = retryScope(function)
        val retryUntilMs = retryAfterUntilMsByScope[retryScope] ?: 0L
        val waitMs = (retryUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
        if (waitMs > 0L) delay(waitMs)
    }

    private fun updateRetryWindow(retryScope: String, retryAfterMs: Long) {
        val target = System.currentTimeMillis() + retryAfterMs
        retryAfterUntilMsByScope.compute(retryScope) { _, current ->
            maxOf(current ?: 0L, target)
        }
    }

    private fun retryScope(function: TdApi.Function<*>): String = when (function) {
        is TdApi.GetInlineQueryResults -> "inline_query_results"
        is TdApi.SearchMessages -> "search_messages"
        is TdApi.SearchChats -> "search_chats"
        is TdApi.SearchPublicChat -> "search_public_chat"
        is TdApi.GetUserFullInfo -> "user_full_info"
        is TdApi.GetUser -> "user"
        is TdApi.GetChat -> "chat"
        is TdApi.LoadChats -> "load_chats"
        else -> function.javaClass.simpleName
    }

    private fun parseRetryAfterMs(message: String?): Long {
        val seconds = Regex("retry after\\s+(\\d+)", RegexOption.IGNORE_CASE)
            .find(message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 1L
        return (seconds * 1000L).coerceAtMost(60_000L)
    }
}