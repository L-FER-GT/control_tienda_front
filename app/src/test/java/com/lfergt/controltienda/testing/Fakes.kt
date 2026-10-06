package com.lfergt.controltienda.testing

import com.lfergt.controltienda.domain.model.AppNotification
import com.lfergt.controltienda.domain.model.AuthSession
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.MeasureUnit
import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.domain.model.OrderDraft
import com.lfergt.controltienda.domain.model.PriceHistoryEntry
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.ProductDraft
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreDraft
import com.lfergt.controltienda.domain.model.StoreListItem
import com.lfergt.controltienda.domain.model.UserProfile
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.port.NotificationRepository
import com.lfergt.controltienda.domain.port.OrderRepository
import com.lfergt.controltienda.domain.port.StoreRepository
import com.lfergt.controltienda.domain.port.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/* Dobles en memoria de los puertos del dominio para probar ViewModels sin Firebase. */

fun testStore(id: String = "s1", name: String = "Bodega Rosita") =
    Store(id, name, "Jr. Lima 123", null, isPublic = false, currency = "PEN", ownerId = "owner", ownerName = "Rosa")

fun testProduct(id: String, name: String = id, price: Long = 100, barcode: String? = null, qr: String? = null, cost: Long? = null) =
    Product(id, name, null, price, cost, MeasureUnit.UNIT, stock = 10.0, stockAlert = null, barcode = barcode, qrCode = qr, photoPath = null)

class FakeAuthRepository(initial: AuthSession? = AuthSession("u1", "u1@test.com", "Ana", isSuperadmin = false)) : AuthRepository {
    val state = MutableStateFlow(initial)
    override val session: Flow<AuthSession?> = state
    override fun currentSession(): AuthSession? = state.value
    override suspend fun signInWithEmail(email: String, password: String) = Unit
    override suspend fun registerWithEmail(displayName: String, email: String, password: String) = Unit
    override suspend fun signInWithGoogle(idToken: String) = Unit
    override suspend fun sendPasswordReset(email: String) = Unit
    override suspend fun refreshSession(): AuthSession? = state.value
    override suspend fun signOut() {
        state.value = null
    }
}

class FakeStoreRepository(
    private val store: Store = testStore(),
    private val access: StoreAccess = StoreAccess.VISITOR,
) : StoreRepository {
    val list = MutableStateFlow<List<StoreListItem>>(emptyList())
    override fun observeStoreList(uid: String): Flow<List<StoreListItem>> = list
    override fun observeStore(storeId: String): Flow<Store?> = flowOf(store)
    override fun observeAccess(storeId: String, uid: String): Flow<StoreAccess> = flowOf(access)
    override suspend fun createStore(draft: StoreDraft, photo: LocalFile?): String = "new"
    override suspend fun updateStore(storeId: String, draft: StoreDraft, photo: LocalFile?) = Unit
}

class FakeCatalogRepository(products: List<Product> = emptyList(), categories: List<Category> = emptyList()) : CatalogRepository {
    val products = MutableStateFlow(products)
    val categories = MutableStateFlow(categories)
    val savedProducts = mutableListOf<ProductDraft>()
    /** Si se asigna, `saveProduct` espera a que se complete (simula latencia). */
    var saveGate: CompletableDeferred<Unit>? = null
    override fun observeCategories(storeId: String): Flow<List<Category>> = categories
    override fun observeProducts(storeId: String): Flow<List<Product>> = products
    override fun observePriceHistory(storeId: String, productId: String): Flow<List<PriceHistoryEntry>> = flowOf(emptyList())
    override suspend fun saveCategory(storeId: String, categoryId: String?, name: String, photo: LocalFile?): String = "c"
    override suspend fun deleteCategory(storeId: String, categoryId: String) = Unit
    override suspend fun assignCategory(storeId: String, productIds: List<String>, categoryId: String?) = Unit
    override suspend fun saveProduct(storeId: String, draft: ProductDraft, photo: LocalFile?): String {
        saveGate?.await()
        savedProducts += draft
        return "p"
    }
    override suspend fun deleteProduct(storeId: String, productId: String) = Unit
}

class FakeOrderRepository : OrderRepository {
    val created = mutableListOf<OrderDraft>()
    /** Si se asigna, `createOrder` espera a que se complete (simula latencia). */
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun createOrder(storeId: String, draft: OrderDraft): String {
        gate?.await()
        created += draft
        return "o${created.size}"
    }
    override fun observeMyOrders(storeId: String, uid: String): Flow<List<Order>> = flowOf(emptyList())
    override fun observeOrder(storeId: String, orderId: String): Flow<Order?> = flowOf(null)
    override suspend fun getOrders(storeId: String, fromMillis: Long, toMillis: Long): List<Order> = emptyList()
}

class FakeUserRepository(private val me: UserProfile = UserProfile("u1", "Ana", "u1@test.com", null, null, "1234567890")) : UserRepository {
    override fun observeMe(): Flow<UserProfile?> = flowOf(me)
    override suspend fun ensureProfile(displayName: String?): UserProfile = me
    override suspend fun updateProfile(displayName: String, phone: String?) = Unit
    override suspend fun updatePhoto(photo: LocalFile) = Unit
    override suspend fun findByCode(code: String): PublicProfile? = null
    override suspend fun searchByName(query: String, limit: Int): List<PublicProfile> = emptyList()
    override suspend fun deleteAccount() = Unit
}

class FakeNotificationRepository(unread: Int = 0) : NotificationRepository {
    val unread = MutableStateFlow(unread)
    override fun observeNotifications(): Flow<List<AppNotification>> = flowOf(emptyList())
    override fun observeUnreadCount(): Flow<Int> = unread
    override suspend fun markRead(notificationId: String) = Unit
    override suspend fun markAllRead() = Unit
    override suspend fun delete(notificationId: String) = Unit
    override suspend fun respondInvitation(storeId: String, invitationId: String, accept: Boolean) = Unit
}
