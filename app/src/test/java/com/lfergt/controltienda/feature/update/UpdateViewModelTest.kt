package com.lfergt.controltienda.feature.update

import app.cash.turbine.test
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.AppUpdate
import com.lfergt.controltienda.domain.model.AppVersion
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.port.AppUpdates
import com.lfergt.controltienda.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdateViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val update = AppUpdate(AppVersion(1, 1, 0), "Novedades", "https://github.com/x.apk", 1_000, null)
    private val file = LocalFile("content://updates/x.apk", "application/vnd.android.package-archive")

    private class FakeUpdates(var newer: AppUpdate?, var failure: Exception? = null) : AppUpdates {
        var checks = 0
        /** La descarga se detiene a la mitad hasta que el test lo complete. */
        val halfway = CompletableDeferred<Unit>()
        override val enabled = true
        override suspend fun findNewer(): AppUpdate? { checks++; failure?.let { throw it }; return newer }
        override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): LocalFile {
            failure?.let { throw it }
            onProgress(0.5f)
            halfway.await()
            onProgress(1f)
            return LocalFile("content://updates/x.apk", "application/vnd.android.package-archive")
        }
    }

    @Test
    fun `al abrir la app ofrece la versión nueva una sola vez`() = runTest {
        val updates = FakeUpdates(update)
        val vm = UpdateViewModel(updates)
        vm.checkOnStart(); vm.dismiss(); vm.checkOnStart()
        assertEquals(1, updates.checks)
        assertEquals(UpdateState.Idle, vm.state.value)
    }

    @Test
    fun `sin conexión al abrir la app no muestra errores`() = runTest {
        val vm = UpdateViewModel(FakeUpdates(null, failure = DomainError.Network()))
        vm.messages.test {
            vm.checkOnStart()
            assertEquals(UpdateState.Idle, vm.state.value)
            expectNoEvents()
        }
    }

    @Test
    fun `la búsqueda manual avisa si ya está al día`() = runTest {
        val vm = UpdateViewModel(FakeUpdates(null))
        vm.messages.test {
            vm.check()
            assertTrue(awaitItem().startsWith("Ya tienes la última versión"))
        }
        assertEquals(UpdateState.Idle, vm.state.value)
    }

    @Test
    fun `descarga mostrando el progreso y queda lista para instalar`() = runTest {
        val updates = FakeUpdates(update)
        val vm = UpdateViewModel(updates)
        vm.check()
        assertEquals(UpdateState.Available(update), vm.state.value)
        vm.download()
        assertEquals(UpdateState.Downloading(update, 50), vm.state.value)
        vm.dismiss()
        assertEquals("No se cierra mientras descarga", UpdateState.Downloading(update, 50), vm.state.value)
        updates.halfway.complete(Unit)
        assertEquals(UpdateState.Ready(update, file), vm.state.value)
    }

    @Test
    fun `si la descarga falla se puede reintentar`() = runTest {
        val updates = FakeUpdates(update)
        val vm = UpdateViewModel(updates)
        vm.check()
        updates.failure = DomainError.Network()
        vm.download()
        assertEquals(UpdateState.Available(update), vm.state.value)
    }
}
