package com.lfergt.controltienda.domain.port

import com.lfergt.controltienda.domain.model.AppNotification
import com.lfergt.controltienda.domain.model.AuthSession
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.Invitation
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.domain.model.OrderDraft
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.PriceHistoryEntry
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.ProductDraft
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.Reception
import com.lfergt.controltienda.domain.model.ReceptionDraft
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreDraft
import com.lfergt.controltienda.domain.model.StoreListItem
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.model.Supplier
import com.lfergt.controltienda.domain.model.UsageReport
import com.lfergt.controltienda.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/*
 * Puertos de salida (driven ports) del hexágono.
 * El dominio y los ViewModels dependen solo de estas interfaces; el módulo :data las implementa.
 * Las operaciones de escritura marcadas como "offline" se guardan localmente y se envían al reconectar.
 */

interface AuthRepository {
    val session: Flow<AuthSession?>
    fun currentSession(): AuthSession?
    suspend fun signInWithEmail(email: String, password: String)
    suspend fun registerWithEmail(displayName: String, email: String, password: String)
    suspend fun signInWithGoogle(idToken: String)
    suspend fun sendPasswordReset(email: String)
    /** Vuelve a leer los custom claims (p. ej. superadmin) del token. */
    suspend fun refreshSession(): AuthSession?
    suspend fun signOut()
}

interface UserRepository {
    fun observeMe(): Flow<UserProfile?>
    /** Crea el perfil y el código de 10 dígitos si no existen (requiere conexión la primera vez). */
    suspend fun ensureProfile(displayName: String? = null): UserProfile
    suspend fun updateProfile(displayName: String, phone: String?)
    suspend fun updatePhoto(photo: LocalFile)
    suspend fun findByCode(code: String): PublicProfile?
    suspend fun searchByName(query: String, limit: Int = 20): List<PublicProfile>
    suspend fun deleteAccount()
}

interface StoreRepository {
    /** Tiendas públicas + tiendas donde el usuario es miembro. */
    fun observeStoreList(uid: String): Flow<List<StoreListItem>>
    fun observeStore(storeId: String): Flow<Store?>
    fun observeAccess(storeId: String, uid: String): Flow<StoreAccess>
    /** Offline. Devuelve el id de la nueva tienda. */
    suspend fun createStore(draft: StoreDraft, photo: LocalFile?): String
    /** Offline. */
    suspend fun updateStore(storeId: String, draft: StoreDraft, photo: LocalFile?)
}

interface MemberRepository {
    fun observeMembers(storeId: String): Flow<List<Membership>>
    fun observePendingInvitations(storeId: String): Flow<List<Invitation>>
    suspend fun invite(store: Store, target: PublicProfile, role: StoreRole)
    suspend fun cancelInvitation(storeId: String, invitationId: String)
    suspend fun setActive(storeId: String, uid: String, active: Boolean)
    suspend fun setPermissions(storeId: String, uid: String, permissions: Set<Permission>)
}

interface NotificationRepository {
    fun observeNotifications(): Flow<List<AppNotification>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markRead(notificationId: String)
    suspend fun markAllRead()
    suspend fun delete(notificationId: String)
    /** Requiere conexión: el servidor crea la membresía. */
    suspend fun respondInvitation(storeId: String, invitationId: String, accept: Boolean)
}

interface CatalogRepository {
    fun observeCategories(storeId: String): Flow<List<Category>>
    fun observeProducts(storeId: String): Flow<List<Product>>
    fun observePriceHistory(storeId: String, productId: String): Flow<List<PriceHistoryEntry>>
    suspend fun saveCategory(storeId: String, categoryId: String?, name: String, photo: LocalFile?): String
    /** Los productos de la categoría quedan sin categoría. */
    suspend fun deleteCategory(storeId: String, categoryId: String)
    suspend fun assignCategory(storeId: String, productIds: List<String>, categoryId: String?)
    suspend fun saveProduct(storeId: String, draft: ProductDraft, photo: LocalFile?): String
    suspend fun deleteProduct(storeId: String, productId: String)
}

interface OrderRepository {
    /** Offline: el número correlativo lo asigna el servidor al sincronizar. */
    suspend fun createOrder(storeId: String, draft: OrderDraft): String
    fun observeMyOrders(storeId: String, uid: String): Flow<List<Order>>
    fun observeOrder(storeId: String, orderId: String): Flow<Order?>
    suspend fun getOrders(storeId: String, fromMillis: Long, toMillis: Long): List<Order>
}

interface SupplierRepository {
    /** Incluye siempre al proveedor "Otros" al inicio. */
    fun observeSuppliers(storeId: String): Flow<List<Supplier>>
    suspend fun saveSupplier(storeId: String, supplier: Supplier): String
    suspend fun deleteSupplier(storeId: String, supplierId: String)
}

interface ReceptionRepository {
    fun observeReceptions(storeId: String): Flow<List<Reception>>
    fun observeReception(storeId: String, receptionId: String): Flow<Reception?>
    suspend fun saveReception(storeId: String, draft: ReceptionDraft): String
    suspend fun getReceptions(storeId: String, fromMillis: Long, toMillis: Long): List<Reception>
}

/** Opciones maestras: solo para el superadmin (creador de la app). */
interface AdminRepository {
    suspend fun getUsage(): UsageReport
    suspend fun searchUsers(query: String): List<PublicProfile>
    suspend fun setUserDisabled(uid: String, disabled: Boolean)
    suspend fun searchStores(query: String): List<Store>
    suspend fun setStoreDisabled(storeId: String, disabled: Boolean)
}
