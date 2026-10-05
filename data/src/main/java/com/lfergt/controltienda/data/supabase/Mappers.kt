package com.lfergt.controltienda.data.supabase

import com.lfergt.controltienda.domain.model.AppNotification
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.Invitation
import com.lfergt.controltienda.domain.model.InvitationStatus
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.NotificationType
import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.domain.model.OrderItem
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.PriceChangeSource
import com.lfergt.controltienda.domain.model.PriceHistoryEntry
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.Reception
import com.lfergt.controltienda.domain.model.ReceptionLine
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.model.Supplier
import com.lfergt.controltienda.domain.model.UserProfile

/*
 * Documento de PostgreSQL <-> modelo del dominio.
 * Las fechas con serverTimestamp() se leen con ESTIMATE para tener un valor aun sin conexión.
 */

private fun DocumentSnapshot.time(field: String): Long =
    getLong(field) ?: 0L

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.maps(field: String): List<Map<String, Any?>> =
    (get(field) as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.strings(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()
private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    uid = id,
    displayName = getString("displayName") ?: "Usuario",
    email = getString("email"),
    phone = getString("phone"),
    photoPath = getString("photoPath"),
    code = getString("code") ?: "",
    isSuperadmin = getBoolean("isSuperadmin") == true,
    disabled = getBoolean("disabled") == true,
)

fun DocumentSnapshot.toPublicProfile(): PublicProfile = PublicProfile(
    uid = id,
    displayName = getString("displayName") ?: "Usuario",
    photoPath = getString("photoPath"),
    code = getString("code") ?: "",
    disabled = getBoolean("disabled") == true,
)

fun DocumentSnapshot.toStore(): Store = Store(
    id = id,
    name = getString("name") ?: "",
    address = getString("address") ?: "",
    photoPath = getString("photoPath"),
    isPublic = getBoolean("isPublic") == true,
    currency = getString("currency") ?: "PEN",
    ownerId = getString("ownerId") ?: "",
    ownerName = getString("ownerName") ?: "",
    disabledBySystem = getBoolean("disabledBySystem") == true,
    updatedAt = time("updatedAt"),
)

/** El id de la tienda es el padre de la colección members. */
fun DocumentSnapshot.toMembership(): Membership = Membership(
    storeId = reference.parent.parent?.id ?: "",
    uid = getString("uid") ?: id,
    role = StoreRole.fromKey(getString("role")) ?: StoreRole.CLIENT,
    permissions = Permission.fromKeys(strings("permissions")),
    active = getBoolean("active") == true,
    displayName = getString("displayName") ?: "Usuario",
    photoPath = getString("photoPath"),
    code = getString("code") ?: "",
    joinedAt = time("joinedAt"),
)

fun DocumentSnapshot.toInvitation(): Invitation = Invitation(
    id = id,
    storeId = getString("storeId") ?: "",
    storeName = getString("storeName") ?: "",
    storePhotoPath = getString("storePhotoPath"),
    fromUid = getString("fromUid") ?: "",
    fromName = getString("fromName") ?: "",
    toUid = getString("toUid") ?: "",
    toName = getString("toName") ?: "",
    toCode = getString("toCode") ?: "",
    role = StoreRole.fromKey(getString("role")) ?: StoreRole.CLIENT,
    status = InvitationStatus.fromKey(getString("status")),
    createdAt = time("createdAt"),
)

fun DocumentSnapshot.toNotification(): AppNotification = AppNotification(
    id = id,
    type = NotificationType.fromKey(getString("type")),
    title = getString("title") ?: "",
    body = getString("body") ?: "",
    fromUid = getString("fromUid"),
    fromName = getString("fromName"),
    fromPhotoPath = getString("fromPhotoPath"),
    invitationId = getString("invitationId"),
    invitationStatus = getString("invitationStatus")?.let(InvitationStatus::fromKey),
    storeId = getString("storeId"),
    read = getBoolean("read") == true,
    createdAt = time("createdAt"),
)

fun DocumentSnapshot.toCategory(): Category = Category(
    id = id,
    name = getString("name") ?: "",
    photoPath = getString("photoPath"),
    updatedAt = time("updatedAt"),
)

fun DocumentSnapshot.toProduct(): Product = Product(
    id = id,
    name = getString("name") ?: "",
    categoryId = getString("categoryId"),
    salePriceCents = getLong("salePriceCents") ?: 0,
    purchaseCostCents = getLong("purchaseCostCents"),
    unit = MeasureUnit.fromKey(getString("unit")),
    stock = getDouble("stock"),
    stockAlert = getDouble("stockAlert"),
    barcode = getString("barcode"),
    qrCode = getString("qrCode"),
    photoPath = getString("photoPath"),
    updatedAt = time("updatedAt"),
)

fun DocumentSnapshot.toPriceHistory(): PriceHistoryEntry = PriceHistoryEntry(
    id = id,
    salePriceCents = getLong("salePriceCents"),
    purchaseCostCents = getLong("purchaseCostCents"),
    source = PriceChangeSource.fromKey(getString("source")),
    refId = getString("refId"),
    changedByName = getString("changedByName") ?: "",
    at = time("at"),
)

fun OrderItem.toMap(): Map<String, Any?> = mapOf(
    "productId" to productId,
    "description" to description,
    "categoryId" to categoryId,
    "categoryName" to categoryName,
    "quantity" to quantity,
    "unit" to unit.key,
    "unitPriceCents" to unitPriceCents,
    "unitCostCents" to unitCostCents,
    "subtotalCents" to subtotalCents,
    "manual" to manual,
)

private fun Map<String, Any?>.toOrderItem(): OrderItem = OrderItem(
    productId = string("productId"),
    description = string("description") ?: "",
    categoryId = string("categoryId"),
    categoryName = string("categoryName"),
    quantity = double("quantity") ?: 0.0,
    unit = MeasureUnit.fromKey(string("unit")),
    unitPriceCents = long("unitPriceCents") ?: 0,
    unitCostCents = long("unitCostCents"),
    manual = this["manual"] == true,
)

fun DocumentSnapshot.toOrder(): Order {
    val number = getLong("number")
    return Order(
        id = id,
        number = number,
        storeId = reference.parent.parent?.id ?: "",
        createdBy = getString("createdBy") ?: "",
        createdByName = getString("createdByName") ?: "",
        createdAt = time("createdAt"),
        paymentMethod = PaymentMethod.fromKey(getString("paymentMethod")),
        currency = getString("currency") ?: "PEN",
        items = maps("items").map { it.toOrderItem() },
        totalCents = getLong("totalCents") ?: 0,
        pendingSync = metadata.hasPendingWrites() || number == null,
    )
}

fun DocumentSnapshot.toSupplier(): Supplier = Supplier(
    id = id,
    companyName = getString("companyName") ?: "",
    ruc = getString("ruc"),
    phone = getString("phone"),
    contactName = getString("contactName"),
    email = getString("email"),
    address = getString("address"),
    notes = getString("notes"),
)

fun ReceptionLine.toMap(): Map<String, Any?> = mapOf(
    "productId" to productId,
    "productName" to productName,
    "quantity" to quantity,
    "unit" to unit.key,
    "unitCostCents" to unitCostCents,
)

fun DocumentSnapshot.toReception(): Reception = Reception(
    id = id,
    supplierId = getString("supplierId") ?: Supplier.OTHERS_ID,
    supplierName = getString("supplierName") ?: "Otros",
    lines = maps("lines").map {
        ReceptionLine(
            productId = it.string("productId") ?: "",
            productName = it.string("productName") ?: "",
            quantity = it.double("quantity") ?: 0.0,
            unit = MeasureUnit.fromKey(it.string("unit")),
            unitCostCents = it.long("unitCostCents"),
        )
    },
    invoiceTotalCents = getLong("invoiceTotalCents"),
    invoicePhotos = strings("invoicePhotos"),
    notes = getString("notes"),
    receivedAt = time("receivedAt"),
    createdBy = getString("createdBy") ?: "",
    createdByName = getString("createdByName") ?: "",
    updatedAt = time("updatedAt"),
    pendingSync = metadata.hasPendingWrites(),
)
