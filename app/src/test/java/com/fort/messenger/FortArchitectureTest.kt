package com.fort.messenger

import com.fort.messenger.model.CardType
import com.fort.messenger.model.ContactPass
import com.fort.messenger.model.DecayDuration
import com.fort.messenger.model.MoodEmotion
import com.fort.messenger.model.PassDurationType
import com.fort.messenger.model.ShareCheckItem
import com.fort.messenger.model.WhatINeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FortArchitectureTest {

    @Test
    fun testMandatoryRevocationDisclosureIntegrity() {
        assertEquals(
            "Revoking access prevents future messages through this pass. It does not remotely delete messages or media already stored on the recipient's device.",
            ContactPass.REVOCATION_DISCLOSURE
        )
    }

    @Test
    fun testMandatoryShareCheckDisclosureIntegrity() {
        assertEquals(
            "Automatic checks assist privacy but may not detect all sensitive content.",
            ShareCheckItem.SHARE_CHECK_DISCLOSURE
        )
    }

    @Test
    fun testZeroCorrelationCardFacets() {
        val types = CardType.values()
        assertEquals(4, types.size)
        assertTrue(types.contains(CardType.PERSONAL))
        assertTrue(types.contains(CardType.WORK))
        assertTrue(types.contains(CardType.TRAVEL))
        assertTrue(types.contains(CardType.MARKETPLACE))
    }

    @Test
    fun testContactPassBoundedDurations() {
        val durations = PassDurationType.values()
        assertEquals(4, durations.size)
        assertEquals("One Conversation", PassDurationType.ONE_CONVERSATION.label)
        assertEquals("7 Days", PassDurationType.SEVEN_DAYS.label)
        assertEquals("Custom Duration", PassDurationType.CUSTOM_DURATION.label)
        assertEquals("Ongoing", PassDurationType.ONGOING.label)
    }

    @Test
    fun testMoodRingEmotionsIncludeAngryAndNeedQuiet() {
        val emotions = MoodEmotion.values()
        assertTrue("Mood Ring must include ANGRY emotion", emotions.contains(MoodEmotion.ANGRY))
        assertTrue("Mood Ring must include NEED_QUIET emotion", emotions.contains(MoodEmotion.NEED_QUIET))
        assertTrue(emotions.contains(MoodEmotion.HAPPY))
        assertTrue(emotions.contains(MoodEmotion.SAD))
        assertTrue(emotions.contains(MoodEmotion.STRESSED))
        assertTrue(emotions.contains(MoodEmotion.READY_TO_TALK))
    }

    @Test
    fun testWhatINeedActionableLayerOptions() {
        val needs = WhatINeed.values()
        assertEquals(4, needs.size)
        assertTrue(needs.contains(WhatINeed.LISTEN_TO_ME))
        assertTrue(needs.contains(WhatINeed.DISTRACT_ME))
        assertTrue(needs.contains(WhatINeed.OFFER_ADVICE))
        assertTrue(needs.contains(WhatINeed.GIVE_ME_SPACE))
    }

    @Test
    fun testTemporalDecayOptions() {
        val decays = DecayDuration.values()
        assertEquals(4, decays.size)
        assertTrue(decays.contains(DecayDuration.MINUTES_30))
        assertTrue(decays.contains(DecayDuration.HOURS_2))
        assertTrue(decays.contains(DecayDuration.END_OF_DAY))
        assertTrue(decays.contains(DecayDuration.CUSTOM))
    }
}
