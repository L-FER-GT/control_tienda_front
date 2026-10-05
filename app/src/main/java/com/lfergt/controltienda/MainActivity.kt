package com.lfergt.controltienda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import com.lfergt.controltienda.data.supabase.SupabaseAuth
import com.lfergt.controltienda.domain.error.userMessage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.ConnectivityMonitor
import com.lfergt.controltienda.domain.port.SyncMonitor
import com.lfergt.controltienda.feature.update.UpdateDialogs
import com.lfergt.controltienda.feature.update.UpdateViewModel
import com.lfergt.controltienda.navigation.AppNavHost
import com.lfergt.controltienda.navigation.HomeRoute
import com.lfergt.controltienda.navigation.LoginRoute
import com.lfergt.controltienda.navigation.NotificationsRoute
import com.lfergt.controltienda.ui.components.LocalOffline
import com.lfergt.controltienda.ui.components.LocalSnackbar
import com.lfergt.controltienda.ui.theme.ControlTiendaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var connectivity: ConnectivityMonitor
    @Inject lateinit var sync: SyncMonitor
    @Inject lateinit var supabase: SupabaseAuth

    private var openNotifications by mutableStateOf(false)
    private var recoveryLink by mutableStateOf<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recoveryLink = intent?.data?.takeIf { it.scheme == "controltienda" }
        openNotifications = intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true
        // Con sesión guardada se entra directo a la lista de tiendas: el login es solo la primera vez.
        val start: Any = if (auth.currentSession() != null) HomeRoute else LoginRoute
        setContent {
            ControlTiendaTheme {
                AppRoot(start)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recoveryLink = intent.data?.takeIf { it.scheme == "controltienda" }
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) openNotifications = true
    }

    @Composable
    private fun AppRoot(start: Any) {
        val navController = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val recoveryScope = rememberCoroutineScope()
        var recovering by remember { mutableStateOf(false) }
        var newPassword by remember { mutableStateOf("") }
        var savingPassword by remember { mutableStateOf(false) }
        LaunchedEffect(recoveryLink) {
            val link = recoveryLink ?: return@LaunchedEffect
            try { supabase.recoverySession(link); recovering = true }
            catch (e: Exception) { snackbar.showSnackbar(e.userMessage()) }
            finally { recoveryLink = null }
        }
        if (recovering) AlertDialog(
            onDismissRequest = {}, title = { Text("Nueva contraseña") },
            text = { OutlinedTextField(value=newPassword,onValueChange={newPassword=it},label={Text("Mínimo 8 caracteres")},singleLine=true,
                visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password)) },
            confirmButton = { TextButton(enabled=!savingPassword && newPassword.length>=8,onClick={
                recoveryScope.launch {
                    savingPassword=true
                    try { supabase.updatePassword(newPassword); recovering=false; newPassword=""; auth.signOut(); snackbar.showSnackbar("Contraseña actualizada. Inicia sesión.") }
                    catch(e: Exception) { snackbar.showSnackbar(e.userMessage()) }
                    finally { savingPassword=false }
                }
            }) { Text("Guardar") } },
        )
        val online by connectivity.isOnline.collectAsStateWithLifecycle(initialValue = true)
        val session by auth.session.collectAsStateWithLifecycle(initialValue = auth.currentSession())
        var wasOffline by remember { mutableStateOf(false) }

        LaunchedEffect(online) {
            if (!online) {
                wasOffline = true
                snackbar.showSnackbar("Sin conexión. Puedes seguir trabajando: los cambios se enviarán al reconectar.")
            } else if (wasOffline) {
                wasOffline = false
                snackbar.showSnackbar("Conexión restablecida. Sincronizando cambios…")
            }
        }
        LaunchedEffect(Unit) { sync.failures.collect { snackbar.showSnackbar(it) } }

        // Al abrir la app se consulta el último release de GitHub (solo en el build release).
        val updater: UpdateViewModel = hiltViewModel()
        LaunchedEffect(Unit) { updater.checkOnStart() }

        // Si la sesión se cierra (o se deshabilita la cuenta), vuelve al login.
        LaunchedEffect(session?.uid) {
            val uid = session?.uid
            if (uid == null) {
                if (navController.currentDestination != null && navController.currentBackStackEntry?.destination?.route?.contains("LoginRoute") != true) {
                    navController.navigate(LoginRoute) { popUpTo(0) { inclusive = true } }
                }
            }
        }

        LaunchedEffect(openNotifications, session?.uid) {
            if (openNotifications && session != null) {
                openNotifications = false
                navController.navigate(NotificationsRoute)
            }
        }

        CompositionLocalProvider(LocalSnackbar provides snackbar, LocalOffline provides !online) {
            AppNavHost(navController = navController, startDestination = start)
            UpdateDialogs(updater)
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    }
}
