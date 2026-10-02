package com.lfergt.controltienda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.ConnectivityMonitor
import com.lfergt.controltienda.domain.port.SyncMonitor
import com.lfergt.controltienda.domain.port.UserRepository
import com.lfergt.controltienda.navigation.AppNavHost
import com.lfergt.controltienda.navigation.HomeRoute
import com.lfergt.controltienda.navigation.LoginRoute
import com.lfergt.controltienda.navigation.NotificationsRoute
import com.lfergt.controltienda.ui.components.LocalOffline
import com.lfergt.controltienda.ui.components.LocalSnackbar
import com.lfergt.controltienda.ui.theme.ControlTiendaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var users: UserRepository
    @Inject lateinit var connectivity: ConnectivityMonitor
    @Inject lateinit var sync: SyncMonitor

    private var openNotifications by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) openNotifications = true
    }

    @Composable
    private fun AppRoot(start: Any) {
        val navController = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
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

        // Si la sesión se cierra (o se deshabilita la cuenta), vuelve al login.
        LaunchedEffect(session?.uid) {
            val uid = session?.uid
            if (uid == null) {
                if (navController.currentDestination != null && navController.currentBackStackEntry?.destination?.route?.contains("LoginRoute") != true) {
                    navController.navigate(LoginRoute) { popUpTo(0) { inclusive = true } }
                }
            } else {
                runCatching { users.registerDeviceToken(FirebaseMessaging.getInstance().token.await()) }
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
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    }
}
