package com.fort.messenger.ui.screens.chats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.ChatConversation
import com.fort.messenger.ui.components.ChatFilter
import com.fort.messenger.ui.components.ConnectionCardBadge
import com.fort.messenger.ui.components.FilterChipBar
import com.fort.messenger.ui.components.FortChatEmptyHero
import com.fort.messenger.ui.components.FortChatLogoBadge
import com.fort.messenger.ui.components.MoodRingBadge
import com.fort.messenger.ui.components.PassCountdownChip
import com.fort.messenger.ui.components.PrivateByDefaultBadge
import com.fort.messenger.ui.components.QuietPresenceBanner
import com.fort.messenger.ui.components.SovereignCard
import com.fort.messenger.ui.components.iceBlueLiquidBackground
import com.fort.messenger.ui.theme.EmeraldVerified
import com.fort.messenger.ui.theme.RoseDestructive
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.viewmodel.AppLanguage
import com.fort.messenger.viewmodel.FortMainViewModel

@Composable
fun ChatsHomeScreen(
    viewModel: FortMainViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToYou: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = com.fort.messenger.ui.components.isAppDarkTheme()
    val isBangla = uiState.currentLanguage == AppLanguage.BANGLA
    val activeCard = uiState.connectionCards.find { it.id == uiState.activeCardId }

    val hasConversations = uiState.conversations.isNotEmpty()
    val filteredConversations = uiState.conversations.filter { conv ->
        when (uiState.selectedFilter) {
            ChatFilter.ALL -> true
            ChatFilter.UNREAD -> conv.unreadCount > 0
            ChatFilter.ROOMS -> conv.isRoom
            ChatFilter.EXPIRING -> conv.passTimeRemaining.contains("h", ignoreCase = true) || conv.passTimeRemaining.contains("1 Convo", ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            // Fort Chat Top Bar matching reference design
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        0.5.dp,
                        if (isDark) Color(0xFF1E2B47) else Color(0xFFE2E8F0)
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FortChatLogoBadge(size = 38.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Fort Chat",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.2).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            PrivateByDefaultBadge()
                        }
                    }

                    // Profile Circle Icon Button (min 48dp touch target)
                    IconButton(
                        onClick = onNavigateToYou,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .border(
                                    0.5.dp,
                                    if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeCard != null && activeCard.avatarEmoji.isNotBlank()) {
                                Text(text = activeCard.avatarEmoji, fontSize = 16.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = if (isBangla) "প্রোফাইল ও এক্সেস ম্যাপ" else "Profile & Access Map",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Hide new-chat button when no conversations exist as required by reference design
            if (hasConversations) {
                FloatingActionButton(
                    onClick = { viewModel.openNewChatMenu() },
                    containerColor = RoyalBluePrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (isBangla) "নতুন চ্যাট" else "New Chat"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBangla) "নতুন চ্যাট" else "New Chat",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier.iceBlueLiquidBackground(isDark)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Page Header: "Your chats"
            item {
                Text(
                    text = if (isBangla) "আপনার চ্যাটসমূহ" else "Your chats",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            // Quiet Presence Control with clear audience and expiry explanation
            item {
                QuietPresenceBanner(
                    moodState = uiState.moodState,
                    onOpenMoodPicker = { viewModel.openMoodPicker() },
                    language = uiState.currentLanguage
                )
            }

            // When there are no conversations at all: Hide filters, counts, and FAB. Show clean liquid-glass hero.
            if (!hasConversations) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Concentric liquid rings with bubbles & shield
                        FortChatEmptyHero(modifier = Modifier.padding(bottom = 18.dp))

                        Text(
                            text = if (isBangla) "এখনো কোনো কথোপকথন নেই" else "No conversations yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isBangla)
                                "একটি গোপন চ্যাট শুরু করতে বিশ্বস্ত কারো সাথে যুক্ত হন।"
                            else
                                "Connect with someone you trust to start a private chat.",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action 1: "Scan an invite" (Royal blue primary pill button, >=48dp touch target)
                        Button(
                            onClick = { viewModel.openPassScanner() },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBangla) "ইনভাইট স্ক্যান করুন" else "Scan an invite",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action 2: "Create an invite" (Outlined pill button, >=48dp touch target)
                        OutlinedButton(
                            onClick = { viewModel.openPassGenerator() },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.2.dp, if (isDark) Color(0xFF3B82F6) else RoyalBluePrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isDark) Color(0xFF60A5FA) else RoyalBluePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBangla) "ইনভাইট তৈরি করুন" else "Create an invite",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFF60A5FA) else RoyalBluePrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action 3: "Find by Fort ID (Knock First)" (Outlined pill button, >=48dp touch target)
                        OutlinedButton(
                            onClick = { viewModel.openSearchPeople() },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonSearch,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBangla) "ফোর্ট আইডি দিয়ে খুঁজুন (নক ফার্স্ট)" else "Find by Fort ID (Knock First)",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Conversations exist: Show Filter Chips Bar
                item {
                    FilterChipBar(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = { viewModel.selectFilter(it) }
                    )
                }

                // Conversation Count Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBangla) "সক্রিয় চ্যাটসমূহ" else "Active Conversations",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${filteredConversations.size} ${if (filteredConversations.size == 1) "chat" else "chats"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // If filter has no matches, show separate filter empty state
                if (filteredConversations.isEmpty()) {
                    item {
                        SovereignCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterAltOff,
                                        contentDescription = null,
                                        tint = RoyalBluePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isBangla) "কোনো মেলানো কথোপকথন নেই" else "No matching conversations",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBangla)
                                        "আপনার ফিল্টারের সাথে কোনো চ্যাট মেলেনি।"
                                    else
                                        "No conversations match your selected filter.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.selectFilter(ChatFilter.ALL) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text(
                                        text = if (isBangla) "সকল কথোপকথন দেখুন" else "Show all conversations",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredConversations, key = { it.id }) { conversation ->
                        ConversationRowItem(
                            conversation = conversation,
                            onClick = {
                                viewModel.openChat(conversation.id)
                                onNavigateToChat(conversation.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationRowItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    SovereignCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Mood Ring micro-badge anchored at bottom-right
            Box(
                modifier = Modifier.size(50.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (conversation.isRoom) {
                                if (isDark) Color(0xFF1E2B47) else Color(0xFFE0E7FF)
                            } else {
                                if (isDark) Color(0xFF16233B) else Color(0xFFEFF4FF)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.avatarEmoji,
                        fontSize = 22.sp
                    )
                }

                // Mood Ring micro-badge (20x20dp)
                if (conversation.moodEmoji != null) {
                    MoodRingBadge(
                        moodEmoji = conversation.moodEmoji,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = conversation.participantName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (conversation.isRoom) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDark) Color(0xFF1E3A5F) else Color(0xFFEFF6FF))
                                    .border(
                                        0.5.dp,
                                        if (isDark) Color(0xFF3B82F6) else Color(0xFF93C5FD),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "ROOM",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBluePrimary
                                )
                            }
                        } else {
                            ConnectionCardBadge(cardType = conversation.cardType)
                        }
                    }
                    Text(
                        text = conversation.lastMessageTime,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (conversation.isTyping) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = RoyalBluePrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "typing...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = RoyalBluePrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            // Delivery status tick if last message was sent by me
                            if (conversation.lastMessageIsMine) {
                                when (conversation.lastMessageDeliveryStatus) {
                                    "PENDING" -> Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = "Queued",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "SENT" -> Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Sent",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "DELIVERED" -> Icon(
                                        imageVector = Icons.Outlined.DoneAll,
                                        contentDescription = "Delivered",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "READ" -> Icon(
                                        imageVector = Icons.Outlined.DoneAll,
                                        contentDescription = "Read",
                                        tint = EmeraldVerified,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = "Failed",
                                        tint = RoseDestructive,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Attachment Icon Preview
                            if (conversation.lastMessageAttachmentType != null) {
                                when (conversation.lastMessageAttachmentType) {
                                    "IMAGE" -> Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "LOCATION_PIN" -> Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = EmeraldVerified,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = conversation.lastMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (conversation.isRoom) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = conversation.passTimeRemaining,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            PassCountdownChip(
                                timeRemaining = conversation.passTimeRemaining,
                                passType = conversation.passType
                            )
                        }

                        if (conversation.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = RoyalBluePrimary,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = conversation.unreadCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
