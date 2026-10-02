package com.fort.messenger

import com.fort.messenger.model.CardType
import com.fort.messenger.model.ContactPass
import com.fort.messenger.model.DecayDuration
import com.fort.messenger.model.MoodEmotion
import com.fort.messenger.model.PassDurationType
import com.fort.messenger.model.ShareCheckItem
import com.fort.messenger.model.WhatINeed
import com.fort.messenger.viewmodel.FortMainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FortArchitectureTest {

    private lateinit var viewModel: FortMainViewModel

    @Before
    fun setUp() {
        viewModel = FortMainViewModel()
    }

    @Test
    fun testInitialFacetsAreZeroCorrelated() {
        val state = viewModel.uiState.value
        assertEquals(4, state.connectionCards.size)

        // Verify distinct public key fingerprints per card
        val fingerprints = state.connectionCards.map { it.publicKeyFingerprint }
        assertEquals(4, fingerprints.distinct().size)

        // Personal card has mood ring enabled; Work card has mood hidden
        val personalCard = state.connectionCards.find { it.type == CardType.PERSONAL }
        val workCard = state.connectionCards.find { it.type == CardType.WORK }
        assertNotNull(personalCard)
        assertNotNull(workCard)
        assertTrue(personalCard!!.moodRingVisible)
        assertFalse(workCard!!.moodRingVisible)
        assertTrue(workCard.businessHoursOnly)
    }

    @Test
    fun testGenerateContactPassCreatesBoundedToken() {
        val pass = viewModel.generateNewPass(CardType.PERSONAL, PassDurationType.SEVEN_DAYS)
        assertNotNull(pass.token)
        assertTrue(pass.token.startsWith("PASS-"))
        assertEquals(PassDurationType.SEVEN_DAYS, pass.durationType)

        val activePasses = viewModel.uiState.value.activePasses
        assertTrue(activePasses.any { it.token == pass.token })
    }

    @Test
    fun testRevokePassTerminatesAccessDeterministically() {
        val pass = viewModel.generateNewPass(CardType.MARKETPLACE, PassDurationType.ONE_CONVERSATION)
        val initialCount = viewModel.uiState.value.activePasses.size

        viewModel.revokePass(pass.id)
        val updatedCount = viewModel.uiState.value.activePasses.size
        assertEquals(initialCount - 1, updatedCount)
        assertFalse(viewModel.uiState.value.activePasses.any { it.id == pass.id })

        // Check revocation disclosure string
        assertEquals(
            "Revoking access prevents future messages through this pass. It does not remotely delete messages or media already stored on the recipient's device.",
            ContactPass.REVOCATION_DISCLOSURE
        )
    }

    @Test
    fun testMoodRingBroadcastAndClear() {
        viewModel.broadcastMood(
            emotion = MoodEmotion.STRESSED,
            whatINeed = WhatINeed.GIVE_ME_SPACE,
            circleName = "Close Circle",
            duration = DecayDuration.MINUTES_30
        )

        val mood = viewModel.uiState.value.moodState
        assertNotNull(mood)
        assertEquals(MoodEmotion.STRESSED, mood!!.emotion)
        assertEquals(WhatINeed.GIVE_ME_SPACE, mood.whatINeed)
        assertEquals("Close Circle", mood.sharingCircleName)
        assertEquals("30m left", mood.remainingTimeString)

        viewModel.clearMoodRing()
        assertNull(viewModel.uiState.value.moodState)
    }

    @Test
    fun testKnockFirstAcceptOnceCreatesSingleConvoPass() {
        val initialRequests = viewModel.uiState.value.inboundRequests
        assertTrue(initialRequests.isNotEmpty())
        val firstRequest = initialRequests.first()

        viewModel.acceptRequestOnce(firstRequest.id)

        // Request must be removed from sandbox queue
        assertFalse(viewModel.uiState.value.inboundRequests.any { it.id == firstRequest.id })

        // New conversation must be added with 1 Convo pass
        val newConv = viewModel.uiState.value.conversations.first()
        assertEquals(firstRequest.senderName, newConv.participantName)
        assertEquals(PassDurationType.ONE_CONVERSATION, newConv.passType)
        assertEquals("1 Convo", newConv.passTimeRemaining)
    }

    @Test
    fun testKnockFirstGrantSevenDays() {
        val req = viewModel.uiState.value.inboundRequests.last()
        viewModel.grantRequestSevenDays(req.id)

        val newConv = viewModel.uiState.value.conversations.first()
        assertEquals(req.senderName, newConv.participantName)
        assertEquals(PassDurationType.SEVEN_DAYS, newConv.passType)
        assertEquals("7d left", newConv.passTimeRemaining)
    }

    @Test
    fun testShareCheckScrubberRedaction() {
        val items = viewModel.uiState.value.shareCheckItems
        assertTrue(items.isNotEmpty())

        val firstItem = items.first()
        assertFalse(firstItem.isScrubbed)

        viewModel.scrubSensitiveItem(firstItem.id)
        val scrubbedItem = viewModel.uiState.value.shareCheckItems.find { it.id == firstItem.id }
        assertNotNull(scrubbedItem)
        assertTrue(scrubbedItem!!.isScrubbed)

        viewModel.scrubAllSensitiveItems()
        assertTrue(viewModel.uiState.value.shareCheckItems.all { it.isScrubbed })

        // Check mandatory disclosure
        assertEquals(
            "Automatic checks assist privacy but may not detect all sensitive content.",
            ShareCheckItem.SHARE_CHECK_DISCLOSURE
        )
    }

    @Test
    fun testPrivateRoomTaskChecklistToggle() {
        val room = viewModel.uiState.value.privateRooms.first()
        val task = room.tasks.first()
        val originalCompleted = task.isCompleted

        viewModel.toggleRoomTask(room.id, task.id)
        val updatedRoom = viewModel.uiState.value.privateRooms.find { it.id == room.id }
        val updatedTask = updatedRoom!!.tasks.find { it.id == task.id }
        assertEquals(!originalCompleted, updatedTask!!.isCompleted)
    }

    @Test
    fun testSendMessageAndSupportResponse() {
        val conv = viewModel.uiState.value.conversations.first()
        viewModel.openChat(conv.id)

        val initialMessagesCount = conv.messages.size
        viewModel.sendMessage("Hello, zero-knowledge testing!")

        val updatedConv = viewModel.uiState.value.conversations.find { it.id == conv.id }!!
        assertEquals(initialMessagesCount + 1, updatedConv.messages.size)
        assertEquals("Hello, zero-knowledge testing!", updatedConv.lastMessage)

        viewModel.sendSupportResponse("🤍")
        val withSupport = viewModel.uiState.value.conversations.find { it.id == conv.id }!!
        assertEquals("I'm here 🤍", withSupport.lastMessage)
    }
}
