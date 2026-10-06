package com.lfergt.controltienda.ui.common

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.ui.components.LocalSnackbar
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

const val TAG = "ControlTienda"

/** ViewModel base: mensajes de una sola vez (snackbar) y ejecución segura de acciones. */
abstract class BaseViewModel : ViewModel() {
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    protected fun message(text: String) {
        messageChannel.trySend(text)
    }

    /** Ejecuta [block] mostrando el error al usuario si falla. Devuelve false si falló. */
    protected fun launchSafe(onError: (Throwable) -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Acción fallida en ${this@BaseViewModel::class.simpleName}", e)
                onError(e)
                message(e.userMessage())
            }
        }
    }
}

@Composable
fun CollectMessages(viewModel: BaseViewModel) {
    val snackbar = LocalSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
}
