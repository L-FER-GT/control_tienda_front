package com.lfergt.controltienda.domain

import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.domain.model.OrderItem
import com.lfergt.controltienda.domain.model.PaymentMethod
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreListItem
import com.lfergt.controltienda.domain.model.StoreRelation

fun product(
    id: String,
    name: String = "Producto $id",
    price: Long = 100,
    cost: Long? = null,
    stock: Double? = null,
    alert: Double? = null,
    categoryId: String? = null,
    barcode: String? = null,
    qr: String? = null,
    unit: MeasureUnit = MeasureUnit.UNIT,
) = Product(id, name, categoryId, price, cost, unit, stock, alert, barcode, qr, null)

fun store(id: String, name: String = "Tienda $id", isPublic: Boolean = false) =
    Store(id, name, "Av. Siempre Viva 123", null, isPublic, "PEN", "owner-$id", "Dueño")

fun listItem(id: String, relation: StoreRelation, active: Boolean = true, name: String = "Tienda $id") =
    StoreListItem(store(id, name), relation, active)

fun item(
    productId: String?,
    qty: Double,
    price: Long,
    cost: Long? = null,
    categoryId: String? = null,
    description: String = productId ?: "Manual",
) = OrderItem(productId, description, categoryId, null, qty, MeasureUnit.UNIT, price, cost, productId == null)

fun order(
    id: String,
    createdAt: Long,
    items: List<OrderItem>,
    by: String = "u1",
    byName: String = "Ana",
    payment: PaymentMethod = PaymentMethod.CASH,
) = Order(id, 1, "s1", by, byName, createdAt, payment, "PEN", items, items.sumOf { it.subtotalCents }, false)
