package com.lfergt.controltienda.feature.home

import androidx.lifecycle.viewModelScope
import com.lfergt.controltienda.domain.model.StoreListFilter
import com.lfergt.controltienda.domain.model.StoreListItem
import com.lfergt.controltienda.domain.model.StoreRelation
import com.lfergt.controltienda.domain.model.UserProfile
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.NotificationRepository
import com.lfergt.controltienda.domain.port.StoreRepository
import com.lfergt.controltienda.domain.port.UserRepository
import com.lfergt.controltienda.domain.usecase.StoreListFilters
import com.lfergt.controltienda.ui.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val items: List<StoreListItem> = emptyList(),
    val filters: List<StoreListFilter> = listOf(StoreListFilter.ALL),
    val selectedFilter: StoreListFilter = StoreListFilter.ALL,
    val ownedCount: Int = 0,
    val query: String = "",
    val me: UserProfile? = null,
    val isSuperadmin: Boolean = false,
    val unread: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val auth: AuthRepository,
    stores: StoreRepository,
    users: UserRepository,
    notifications: NotificationRepository,
) : BaseViewModel() {

    /** null = todavía no eligió: se usa el filtro por defecto ("Mis tiendas" si tiene alguna). */
    private val selected = MutableStateFlow<StoreListFilter?>(null)
    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allStores = auth.session.flatMapLatest { session ->
        if (session == null) flowOf(null) else stores.observeStoreList(session.uid)
    }

    private val chrome = combine(users.observeMe(), notifications.observeUnreadCount(), auth.session) { me, unread, session ->
        Triple(me, unread, session?.isSuperadmin == true)
    }

    val state: StateFlow<HomeUiState> = combine(allStores, selected, query, chrome) { all, chosen, q, (me, unread, isSuper) ->
        if (all == null) return@combine HomeUiState(loading = true, me = me, unread = unread, isSuperadmin = isSuper)
        val filter = StoreListFilters.resolve(chosen, all)
        HomeUiState(
            loading = false,
            items = StoreListFilters.apply(all, filter, q),
            filters = StoreListFilters.available(all),
            selectedFilter = filter,
            ownedCount = all.count { it.relation == StoreRelation.OWNER },
            query = q,
            me = me,
            isSuperadmin = isSuper,
            unread = unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectFilter(filter: StoreListFilter) {
        selected.value = filter
    }

    fun onQuery(text: String) {
        query.value = text
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
