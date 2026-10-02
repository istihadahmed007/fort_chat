package com.fort.messenger.model

enum class MoodEmotion(
    val emoji: String,
    val title: String,
    val subtitle: String
) {
    ANGRY("😠", "Angry", "Give me some space."),
    HAPPY("😊", "Happy", "Feeling good!"),
    SAD("😔", "Sad", "Could use some company."),
    STRESSED("😣", "Stressed", "Please keep it brief."),
    READY_TO_TALK("💬", "Ready to talk", "Anyone free?"),
    NEED_QUIET("🌙", "Need quiet", "Taking time for myself.")
}

enum class WhatINeed(val label: String) {
    LISTEN_TO_ME("Listen to me"),
    DISTRACT_ME("Distract me"),
    OFFER_ADVICE("Offer advice"),
    GIVE_ME_SPACE("Give me space")
}

enum class DecayDuration(val label: String, val minutes: Long) {
    MINUTES_30("30 min", 30),
    HOURS_2("2 hrs", 120),
    END_OF_DAY("End of Day", 480),
    CUSTOM("Custom", 720)
}

data class MoodRingState(
    val emotion: MoodEmotion,
    val whatINeed: WhatINeed? = null,
    val sharingCircleName: String = "Close Circle",
    val duration: DecayDuration = DecayDuration.HOURS_2,
    val remainingTimeString: String = "1h 48m left",
    val isActive: Boolean = true
)
