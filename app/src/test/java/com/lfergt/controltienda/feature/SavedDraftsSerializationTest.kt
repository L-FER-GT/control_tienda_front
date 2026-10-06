package com.lfergt.controltienda.feature

import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Supplier
import com.lfergt.controltienda.feature.catalog.ProductForm
import com.lfergt.controltienda.feature.profile.ProfileForm
import com.lfergt.controltienda.feature.purchasing.LineForm
import com.lfergt.controltienda.feature.purchasing.ReceptionForm
import com.lfergt.controltienda.feature.purchasing.SupplierForm
import com.lfergt.controltienda.feature.scanner.ScannedCode
import com.lfergt.controltienda.feature.store.StoreEditorState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Los borradores van al Bundle de SavedStateHandle: deben sobrevivir a la serialización completa. */
class SavedDraftsSerializationTest {

    private fun <T> roundTrip(value: T): T {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(value) } }.toByteArray()
        @Suppress("UNCHECKED_CAST")
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() } as T
    }

    private fun assertSurvives(value: Any) = assertEquals(value, roundTrip(value))

    @Test
    fun `borrador de producto`() = assertSurvives(
        ProductForm(
            loaded = true, name = "Queso", salePrice = "24.00", unit = MeasureUnit.KG, trackStock = true, stock = "3.5",
            pickedPhoto = "content://media/1", errors = buildMap { put("price", "Requerido") },
        ),
    )

    @Test
    fun `borrador de recepción con proveedor, líneas, fotos y código pendiente`() = assertSurvives(
        ReceptionForm(
            loaded = true,
            supplier = Supplier("p1", "Distribuidora", "20123456789", null, null, null, null, null),
            receivedAt = 1_000,
            lines = listOf(LineForm("arroz", "Arroz", MeasureUnit.KG, "2.5", "3.20")),
            keptPhotos = listOf("stores/s1/f1.jpg"),
            newPhotos = listOf("content://media/2"),
            unknownCode = ScannedCode("775", isQr = false),
        ),
    )

    @Test
    fun `borradores de proveedor, tienda y perfil`() {
        assertSurvives(SupplierForm(loaded = true, companyName = "Distribuidora", errors = mapOf("ruc" to "Inválido")))
        assertSurvives(StoreEditorState(name = "Bodega", isPublic = true, pickedPhoto = "content://media/3"))
        assertSurvives(ProfileForm(name = "Ana", phone = "999"))
    }
}
