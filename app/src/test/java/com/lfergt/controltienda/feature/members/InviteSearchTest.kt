package com.lfergt.controltienda.feature.members

import androidx.lifecycle.SavedStateHandle
import com.lfergt.controltienda.domain.model.Invitation
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.model.UserProfile
import com.lfergt.controltienda.domain.port.MemberRepository
import com.lfergt.controltienda.domain.port.UserRepository
import com.lfergt.controltienda.domain.usecase.InviteMemberUseCase
import com.lfergt.controltienda.testing.FakeAuthRepository
import com.lfergt.controltienda.testing.FakeStoreRepository
import com.lfergt.controltienda.testing.MainDispatcherRule
import com.lfergt.controltienda.ui.common.StoreContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InviteSearchTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val ana = PublicProfile("u2", "Ana Torres", null, "1234567890")
    private val byCode = mutableListOf<String>()
    private val byName = mutableListOf<String>()

    private val users = object : UserRepository {
        override fun observeMe(): Flow<UserProfile?> = flowOf(null)
        override suspend fun ensureProfile(displayName: String?): UserProfile = error("no usado")
        override suspend fun updateProfile(displayName: String, phone: String?) = Unit
        override suspend fun updatePhoto(photo: LocalFile) = Unit
        override suspend fun findByCode(code: String): PublicProfile? { byCode += code; return ana.takeIf { code == it.code } }
        override suspend fun searchByName(query: String, limit: Int): List<PublicProfile> { byName += query; return listOf(ana) }
        override suspend fun deleteAccount() = Unit
    }

    private val members = object : MemberRepository {
        override fun observeMembers(storeId: String): Flow<List<Membership>> = flowOf(emptyList())
        override fun observePendingInvitations(storeId: String): Flow<List<Invitation>> = flowOf(emptyList())
        override suspend fun invite(store: Store, target: PublicProfile, role: StoreRole) = Unit
        override suspend fun cancelInvitation(storeId: String, invitationId: String) = Unit
        override suspend fun setActive(storeId: String, uid: String, active: Boolean) = Unit
        override suspend fun setPermissions(storeId: String, uid: String, permissions: Set<Permission>) = Unit
    }

    private fun viewModel() = MembersViewModel(
        SavedStateHandle(mapOf("storeId" to "s1")),
        StoreContext(FakeStoreRepository(access = StoreAccess(StoreRole.OWNER, emptySet(), true)), FakeAuthRepository()),
        members, users, InviteMemberUseCase(members),
    )

    @Test
    fun `solo números, espacios o guiones cuentan como código`() {
        assertTrue(isCodeQuery("123-456 7890"))
        assertTrue(isCodeQuery("12"))
        assertFalse(isCodeQuery("Ana"))
        assertFalse(isCodeQuery("Ana 2"))
        assertFalse(isCodeQuery("  "))
    }

    @Test
    fun `un código escrito con guiones se busca por código`() = runTest {
        val vm = viewModel()
        vm.openInvite(StoreRole.EMPLOYEE)
        vm.onQuery("123-456-7890")
        assertEquals("1234567890", vm.invite.value.query)
        assertTrue(vm.invite.value.byCode)
        advanceTimeBy(500)
        assertEquals(listOf("1234567890"), byCode)
        assertEquals(listOf(ana), vm.invite.value.results)
        assertTrue(byName.isEmpty())
    }

    @Test
    fun `un nombre se busca por nombre en el mismo campo`() = runTest {
        val vm = viewModel()
        vm.openInvite(StoreRole.CLIENT)
        vm.onQuery("Ana")
        assertFalse(vm.invite.value.byCode)
        advanceTimeBy(500)
        assertEquals(listOf("Ana"), byName)
        assertEquals(listOf(ana), vm.invite.value.results)
        assertTrue(byCode.isEmpty())
    }
}
