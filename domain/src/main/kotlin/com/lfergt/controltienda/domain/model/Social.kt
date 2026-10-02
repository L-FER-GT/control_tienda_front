package com.lfergt.controltienda.domain.model

enum class InvitationStatus(val key: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    companion object {
        fun fromKey(key: String?): InvitationStatus = entries.firstOrNull { it.key == key } ?: PENDING
    }
}

data class Invitation(
    val id: String,
    val storeId: String,
    val storeName: String,
    val storePhotoPath: String?,
    val fromUid: String,
    val fromName: String,
    val toUid: String,
    val toName: String,
    val toCode: String,
    val role: StoreRole,
    val status: InvitationStatus,
    val createdAt: Long,
)

enum class NotificationType(val key: String) {
    INVITATION("invitation"),
    INVITATION_RESPONSE("invitation_response"),
    USAGE_ALERT("usage_alert"),
    INFO("info");

    companion object {
        fun fromKey(key: String?): NotificationType = entries.firstOrNull { it.key == key } ?: INFO
    }
}

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val fromUid: String?,
    val fromName: String?,
    val fromPhotoPath: String?,
    val invitationId: String?,
    val invitationStatus: InvitationStatus?,
    val storeId: String?,
    val read: Boolean,
    val createdAt: Long,
)
