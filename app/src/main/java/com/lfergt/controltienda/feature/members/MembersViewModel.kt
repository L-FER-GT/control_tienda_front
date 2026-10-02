package com.lfergt.controltienda.feature.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.Invitation
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.model.UserCode
import com.lfergt.controltienda.domain.port.MemberRepository
import com.lfergt.controltienda.domain.port.UserRepository
import com.lfergt.controltienda.domain.usecase.InviteMemberUseCase
import com.lfergt.controltienda.navigation.MembersRoute
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.StoreContext
import com.lfergt.controltienda.ui.common.StoreHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MembersState(
    val header: StoreHeader = StoreHeader(),
    val members: List<Membership> = emptyList(),
    val pending: List<Invitation> = emptyList(),
    val myUid: String? = null,
)

/** Estado del diálogo de invitación. */
data class InviteState(
    val open: Boolean = false,
    val role: StoreRole = StoreRole.EMPLOYEE,
    val byCode: Boolean = true,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<PublicProfile> = emptyList(),
    val searched: Boolean = false,
    val sending: Boolean = false,
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    savedState: SavedStateHandle,
    context: StoreContext,
    private val members: MemberRepository,
    private val users: UserRepository,
    private val inviteMember: InviteMemberUseCase,
) : BaseViewModel() {

    val storeId = savedState.toRoute<MembersRoute>().storeId
    private val myUid = context.uid

    val state: StateFlow<MembersState> = combine(
        context.header(storeId),
        members.observeMembers(storeId),
        members.observePendingInvitations(storeId),
    ) { header, list, pending -> MembersState(header, list, pending, myUid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MembersState())

    private val _invite = MutableStateFlow(InviteState())
    val invite = _invite.asStateFlow()

    init {
        // Búsqueda por nombre mientras escribe.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _invite.debounce(400).distinctUntilChanged { a, b -> a.query == b.query && a.byCode == b.byCode && a.open == b.open }
                .collect { s -> if (s.open && !s.byCode) searchByName(s.query) }
        }
    }

    fun openInvite(role: StoreRole) = _invite.update { InviteState(open = true, role = role) }
    fun closeInvite() = _invite.update { it.copy(open = false) }
    fun setInviteRole(role: StoreRole) = _invite.update { it.copy(role = role) }
    fun setByCode(byCode: Boolean) = _invite.update { it.copy(byCode = byCode, query = "", results = emptyList(), searched = false) }

    fun onQuery(text: String) {
        val clean = if (_invite.value.byCode) text.filter(Char::isDigit).take(UserCode.LENGTH) else text
        _invite.update { it.copy(query = clean, searched = false, results = if (it.byCode) emptyList() else it.results) }
        if (_invite.value.byCode && clean.length == UserCode.LENGTH) searchByCode(clean)
    }

    private fun searchByCode(code: String) {
        _invite.update { it.copy(searching = true) }
        launchSafe(onError = { _invite.update { s -> s.copy(searching = false) } }) {
            val found = users.findByCode(code)
            _invite.update { it.copy(searching = false, searched = true, results = listOfNotNull(found)) }
        }
    }

    private suspend fun searchByName(query: String) {
        if (query.trim().length < 2) {
            _invite.update { it.copy(results = emptyList(), searched = false) }
            return
        }
        _invite.update { it.copy(searching = true) }
        try {
            val found = users.searchByName(query)
            _invite.update { it.copy(searching = false, searched = true, results = found) }
        } catch (e: Exception) {
            _invite.update { it.copy(searching = false) }
        }
    }

    fun sendInvite(target: PublicProfile) {
        val s = state.value
        val store = s.header.store ?: return
        val uid = myUid ?: return
        _invite.update { it.copy(sending = true) }
        launchSafe(onError = { _invite.update { it.copy(sending = false) } }) {
            inviteMember(store, target, _invite.value.role, uid, s.members, s.pending)
            _invite.update { InviteState() }
            message("Invitación enviada a ${target.displayName}. Le llegará una notificación para aceptarla.")
        }
    }

    fun cancelInvitation(invitation: Invitation) = launchSafe {
        members.cancelInvitation(storeId, invitation.id)
        message("Invitación cancelada")
    }

    fun setActive(member: Membership, active: Boolean) = launchSafe {
        members.setActive(storeId, member.uid, active)
        message(if (active) "${member.displayName} fue habilitado" else "${member.displayName} fue deshabilitado")
    }

    fun setPermissions(member: Membership, permissions: Set<Permission>) = launchSafe {
        members.setPermissions(storeId, member.uid, permissions)
        message("Permisos actualizados")
    }
}
