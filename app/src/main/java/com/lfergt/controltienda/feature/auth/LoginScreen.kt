package com.lfergt.controltienda.feature.auth

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lfergt.controltienda.R
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.components.LocalSnackbar
import kotlinx.coroutines.launch

/** Primera pantalla (solo la primera vez: luego la sesión queda guardada en el celular). */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    CollectMessages(viewModel)
    LaunchedEffect(Unit) { viewModel.loggedIn.collect { onLoggedIn() } }

    Scaffold(snackbarHost = { SnackbarHost(LocalSnackbar.current, Modifier.imePadding()) }) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primary) {
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                    )
                }
                Text("Control Tienda", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (state.registering) "Crea tu cuenta para administrar o unirte a tiendas"
                    else "Inicia sesión para continuar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))

                if (state.registering) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        label = { Text("Nombre completo") },
                        isError = "name" in state.fieldErrors,
                        supportingText = state.fieldErrors["name"]?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmail,
                    label = { Text("Correo electrónico") },
                    isError = "email" in state.fieldErrors,
                    supportingText = state.fieldErrors["email"]?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                    label = { Text("Contraseña") },
                    isError = "password" in state.fieldErrors,
                    supportingText = state.fieldErrors["password"]?.let { { Text(it) } },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Ocultar" else "Mostrar",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { keyboard?.hide(); viewModel.submit() },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text(if (state.registering) "Crear cuenta" else "Ingresar")
                }
                if (!state.registering) {
                    TextButton(onClick = viewModel::forgotPassword) { Text("¿Olvidaste tu contraseña?") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("  o  ", color = MaterialTheme.colorScheme.outline)
                    HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            requestGoogleIdToken(context)
                                .onSuccess { viewModel.signInWithGoogle(it) }
                                .onFailure { e -> e.message?.let(viewModel::onGoogleError) }
                        }
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("G", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text("Continuar con Google")
                }
                TextButton(onClick = viewModel::toggleMode) {
                    Text(if (state.registering) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate")
                }
            }
        }
    }
}

/**
 * Inicio de sesión con Google mediante Credential Manager.
 * Devuelve el ID token que luego se intercambia por una sesión de Supabase.
 */
private suspend fun requestGoogleIdToken(context: Context): Result<String> = runCatching {
    check(context.getString(R.string.default_web_client_id).isNotBlank()) { "El acceso con Google aún no está configurado. Usa correo y contraseña." }
    val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = try {
        CredentialManager.create(context).getCredential(context, request)
    } catch (e: GetCredentialCancellationException) {
        throw IllegalStateException(null as String?)
    } catch (e: androidx.credentials.exceptions.NoCredentialException) {
        throw IllegalStateException("No hay una cuenta de Google disponible. Agrega una en el teléfono o usa correo y contraseña.")
    } catch (e: GetCredentialException) {
        throw IllegalStateException("No se pudo iniciar sesión con Google. Intenta de nuevo o usa correo y contraseña.")
    }
    val credential = result.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } else {
        throw IllegalStateException("Credencial de Google no válida")
    }
}
