package com.fort.messenger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.ui.graphics.vector.ImageVector

enum class FortDestination(
    val route: String,
    val title: String,
    val bengaliTitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHATS("chats", "Chats", "চ্যাট", Icons.Filled.ModeComment, Icons.Outlined.ModeComment),
    CIRCLES("circles", "Circles", "সার্কেল", Icons.Filled.Groups, Icons.Outlined.Groups),
    REQUESTS("requests", "Requests", "অনুরোধ", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    YOU("you", "You", "প্রোফাইল", Icons.Filled.SentimentSatisfiedAlt, Icons.Outlined.SentimentSatisfied)
}
