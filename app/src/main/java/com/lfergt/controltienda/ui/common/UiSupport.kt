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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

private val locale = Locale.forLanguageTag("es-PE")

fun formatDateTime(millis: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(millis))

fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", locale).format(Date(millis))

fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(millis))
