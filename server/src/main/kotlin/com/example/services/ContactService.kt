package com.example.services

import com.example.models.ConnectionRequestResponse
import com.example.models.PublicUser
import com.example.models.SocketEvent
import com.example.repositories.ContactRepository
import com.example.repositories.NotificationRepository
import com.example.utils.NotFoundException
import java.util.UUID

class ContactService(
    private val contacts: ContactRepository = ContactRepository(),
    private val notifications: NotificationRepository = NotificationRepository()
) {

    suspend fun sendRequest(senderId: UUID, receiverId: UUID): ConnectionRequestResponse {
        val request = io { contacts.sendRequest(senderId, receiverId) }

        if (request.status == "accepted") {
            // The other person had already asked us -> tell them we're connected.
            notify(
                userId = receiverId,
                title = "New contact",
                body = "${request.receiver.fullName} accepted your contact request.",
                type = "contact_accepted",
                referenceId = senderId
            )
        } else {
            notify(
                userId = receiverId,
                title = "Contact request",
                body = "${request.sender.fullName} wants to connect with you.",
                type = "contact_request",
                referenceId = UUID.fromString(request.id)
            )
        }
        return request
    }

    suspend fun respond(requestId: UUID, userId: UUID, accept: Boolean): ConnectionRequestResponse {
        val request = io { contacts.respond(requestId, userId, accept) }
        if (accept) {
            notify(
                userId = UUID.fromString(request.sender.id),
                title = "New contact",
                body = "${request.receiver.fullName} accepted your contact request.",
                type = "contact_accepted",
                referenceId = userId
            )
        }
        return request
    }

    suspend fun incoming(userId: UUID) = io { contacts.incomingRequests(userId) }
    suspend fun outgoing(userId: UUID) = io { contacts.outgoingRequests(userId) }
    suspend fun list(userId: UUID): List<PublicUser> = io { contacts.contacts(userId) }

    suspend fun remove(userId: UUID, contactId: UUID) {
        if (!io { contacts.removeContact(userId, contactId) }) throw NotFoundException("Contact not found.")
    }

    private suspend fun notify(userId: UUID, title: String, body: String, type: String, referenceId: UUID?) {
        val notification = io { notifications.create(userId, title, body, type, referenceId) }
        WebSocketHub.sendToUser(userId, SocketEvent(type = "notification", notification = notification))
    }
}
