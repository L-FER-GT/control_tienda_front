package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.Invitation
import com.lfergt.controltienda.domain.model.InvitationStatus
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.ProductDraft
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.ReceptionDraft
import com.lfergt.controltienda.domain.model.ReportFilter
import com.lfergt.controltienda.domain.model.ReportTable
import com.lfergt.controltienda.domain.model.ReportType
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreDraft
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.port.Clock
import com.lfergt.controltienda.domain.port.MemberRepository
import com.lfergt.controltienda.domain.port.OrderRepository
import com.lfergt.controltienda.domain.port.ReceptionRepository
import com.lfergt.controltienda.domain.port.StoreRepository
import javax.inject.Inject

/*
 * Puertos de entrada (casos de uso). Contienen las reglas que no dependen de la interfaz ni de Firebase.
 */

class CreateOrderUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(storeId: String, cart: OrderCart, payment: PaymentMethod, currency: String): String {
        if (cart.isEmpty) throw DomainError.Validation(null, "Agrega al menos un producto a la venta")
        if (cart.lines.any { it.item.quantity <= 0 }) throw DomainError.Validation(null, "Hay cantidades inválidas")
        return orders.createOrder(storeId, cart.toDraft(payment, currency, clock.now()))
    }
}

class SaveProductUseCase @Inject constructor(
    private val catalog: CatalogRepository,
) {
    suspend operator fun invoke(storeId: String, draft: ProductDraft, existing: List<Product>, photo: LocalFile?): String {
        val errors = ProductValidator.validate(draft, existing)
        if (errors.isNotEmpty()) {
            val (field, message) = errors.entries.first()
            throw DomainError.Validation(field, message)
        }
        val normalized = draft.copy(
            name = draft.name.trim(),
            barcode = draft.barcode?.trim()?.ifEmpty { null },
            qrCode = draft.qrCode?.trim()?.ifEmpty { null },
        )
        return catalog.saveProduct(storeId, normalized, photo)
    }
}

class SaveStoreUseCase @Inject constructor(
    private val stores: StoreRepository,
) {
    suspend operator fun invoke(storeId: String?, draft: StoreDraft, photo: LocalFile?): String {
        if (draft.name.isBlank()) throw DomainError.Validation("name", "El nombre es obligatorio")
        if (draft.address.isBlank()) throw DomainError.Validation("address", "La dirección es obligatoria")
        val clean = draft.copy(name = draft.name.trim(), address = draft.address.trim())
        return if (storeId == null) stores.createStore(clean, photo)
        else stores.updateStore(storeId, clean, photo).let { storeId }
    }
}

class InviteMemberUseCase @Inject constructor(
    private val members: MemberRepository,
) {
    suspend operator fun invoke(
        store: Store,
        target: PublicProfile,
        role: StoreRole,
        myUid: String,
        currentMembers: List<Membership>,
        pending: List<Invitation>,
    ) {
        if (role == StoreRole.OWNER) throw DomainError.Validation(null, "Solo puede haber un administrador por tienda")
        if (target.uid == myUid) throw DomainError.Validation(null, "No puedes invitarte a ti mismo")
        if (target.disabled) throw DomainError.Validation(null, "Este usuario está deshabilitado")
        currentMembers.firstOrNull { it.uid == target.uid }?.let {
            if (it.active) throw DomainError.Conflict("${target.displayName} ya es miembro de la tienda")
        }
        if (pending.any { it.toUid == target.uid && it.status == InvitationStatus.PENDING }) {
            throw DomainError.Conflict("Ya hay una invitación pendiente para ${target.displayName}")
        }
        members.invite(store, target, role)
    }
}

class SaveReceptionUseCase @Inject constructor(
    private val receptions: ReceptionRepository,
) {
    suspend operator fun invoke(storeId: String, draft: ReceptionDraft): String {
        if (draft.lines.isEmpty() && draft.keptPhotos.isEmpty() && draft.newPhotos.isEmpty()) {
            throw DomainError.Validation(null, "Agrega productos o una foto de la factura")
        }
        if (draft.lines.any { it.quantity <= 0 }) throw DomainError.Validation(null, "Las cantidades deben ser mayores a 0")
        if ((draft.invoiceTotalCents ?: 0) < 0) throw DomainError.Validation(null, "El total no puede ser negativo")
        val merged = draft.lines.groupBy { it.productId }.map { (_, lines) ->
            lines.last().copy(quantity = lines.sumOf { it.quantity })
        }
        return receptions.saveReception(storeId, draft.copy(lines = merged))
    }
}

class GenerateReportUseCase @Inject constructor(
    private val orders: OrderRepository,
    private val receptions: ReceptionRepository,
    private val clock: Clock,
) {
    private val generator = ReportGenerator()

    suspend operator fun invoke(
        store: Store,
        filter: ReportFilter,
        products: List<Product>,
        categories: List<Category>,
    ): ReportTable {
        if (filter.type.needsDateRange && filter.fromMillis >= filter.toMillis) {
            throw DomainError.Validation(null, "La fecha inicial debe ser anterior a la final")
        }
        val needsOrders = filter.type != ReportType.INVENTORY_VALUE && filter.type != ReportType.PURCHASES_BY_SUPPLIER
        val data = ReportData(
            storeName = store.name,
            currency = store.currency,
            orders = if (needsOrders) orders.getOrders(store.id, filter.fromMillis, filter.toMillis) else emptyList(),
            receptions = if (filter.type == ReportType.PURCHASES_BY_SUPPLIER) {
                receptions.getReceptions(store.id, filter.fromMillis, filter.toMillis)
            } else emptyList(),
            products = products,
            categories = categories,
        )
        return generator.generate(filter, data, clock.now())
    }
}
