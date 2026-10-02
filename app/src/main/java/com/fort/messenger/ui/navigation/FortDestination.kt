package com.fort.messenger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector

enum class FortDestination(
    val route: String,
    val title: String,
    val bengaliTitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHATS("chats", "Chats", "চ্যাট", Icons.Filled.ModeComment, Icons.Outlined.ModeComment),
    CIRCLES("circles", "Circles", "সার্কেল", Icons.Filled.GroupWork, Icons.Outlined.GroupWork),
    REQUESTS("requests", "Requests", "অনুরোধ", Icons.Filled.Mail, Icons.Outlined.Mail),
    YOU("you", "You", "প্রোফাইল", Icons.Filled.Shield, Icons.Outlined.Shield)
}
