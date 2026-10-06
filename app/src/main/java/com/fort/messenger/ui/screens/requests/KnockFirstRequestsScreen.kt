package com.fort.messenger.ui.screens.requests

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.KnockFirstRequest
import com.fort.messenger.ui.components.ConnectionCardBadge
import com.fort.messenger.ui.components.FortConnectionRequestHero
import com.fort.messenger.ui.components.iceBlueLiquidBackground
import com.fort.messenger.ui.theme.RoseDestructive
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.viewmodel.AppLanguage
import com.fort.messenger.viewmodel.FortMainViewModel

@Composable
fun KnockFirstRequestsScreen(
    viewModel: FortMainViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = com.fort.messenger.ui.components.isAppDarkTheme()
    val isBangla = uiState.currentLanguage == AppLanguage.BANGLA
    val requests = uiState.inboundRequests

    Scaffold(
        topBar = {
            // Fort Chat Top Bar with Close (✕) matching Mobile Privacy Showcase
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) Color(0xEB0D1527)
                        else Color.White.copy(alpha = 0.85f)
                    )
                    .border(
                        0.5.dp,
                        if (isDark) Color(0xFF1E2B47) else Color(0xFFE2E8F0)
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Ice-blue shield logo
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = "Fort Chat Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Fort Chat",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText
                        )
                    }

                    // Close (✕) Button matching showcase
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isBangla) "বন্ধ করুন" else "Close",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier.iceBlueLiquidBackground(isDark)
    ) { innerPadding ->
        if (requests.isEmpty()) {
            // Empty queue state styled with orbital hero
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                com.fort.messenger.ui.components.FortKnockFirstOrbitalHero(
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = if (isBangla) "নক ফার্স্ট" else "Knock First",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isBangla)
                        "কেউ নক করলে বা সংযোগের অনুরোধ পাঠালে তাদের বার্তা এখানে পর্যালোচনার জন্য আসবে।"
                    else
                        "Someone would like to start a conversation with you on Fort Chat. Requests will appear here for your review.",
                    fontSize = 13.5.sp,
                    color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseNavySubtext,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(requests, key = { _, req -> req.id }) { index, request ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (requests.size > 1) {
                            Text(
                                text = if (isBangla) "অনুরোধ ${index + 1} / ${requests.size}" else "Request ${index + 1} of ${requests.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseMutedText,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // 3D Orbital Light Ring Hero matching Phone 2
                        com.fort.messenger.ui.components.FortKnockFirstOrbitalHero(
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Title: "Knock First"
                        Text(
                            text = if (isBangla) "নক ফার্স্ট" else "Knock First",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtitle: "Someone would like to start a conversation with you on Fort Chat."
                        Text(
                            text = if (isBangla)
                                "কেউ আপনার সাথে ফোর্ট চ্যাটে কথোপকথন শুরু করতে চান।"
                            else
                                "Someone would like to start a conversation with you on Fort Chat.",
                            fontSize = 13.5.sp,
                            color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseNavySubtext,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Requester Profile Glass Card with 3 stacked pill actions
                        val displayName = request.senderName.ifBlank { "Maya Chen" }
                        val handle = "@" + displayName.lowercase().replace(" ", "")
                        val excerpt = request.rawMessageExcerpt.ifBlank {
                            "Hi! We met at the design summit. I'd love to stay in touch here."
                        }

                        com.fort.messenger.ui.components.FortKnockFirstCard(
                            senderDisplayName = displayName,
                            senderHandle = handle,
                            messageExcerpt = excerpt,
                            timestamp = "2h ago",
                            onAccept = { viewModel.grantRequestSevenDays(request.id) },
                            onDecline = { viewModel.declineRequest(request.id) },
                            onBlock = { viewModel.blockAndReportRequest(request.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionRequestCardItem(
    request: KnockFirstRequest,
    index: Int,
    totalCount: Int,
    onAccept: () -> Unit,
    onAcceptOnce: () -> Unit,
    onDecline: () -> Unit,
    onBlock: () -> Unit,
    isBangla: Boolean,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF131D31) else Color.White
    val cardBorder = if (isDark) Color(0xFF1E3A5F) else Color(0xFFE2E8F0)
    val circleIconBg = if (isDark) Color(0xFF1E3A5F) else Color(0xFFEFF6FF)
    val circleIconBorder = if (isDark) Color(0xFF2563EB).copy(alpha = 0.4f) else Color(0xFFDBEAFE)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (totalCount > 1) {
            Text(
                text = if (isBangla) "অনুরোধ ${index + 1} / $totalCount" else "Request ${index + 1} of $totalCount",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Hero concentric liquid rings with shield and checkmark (Matching Screen 2)
        FortConnectionRequestHero(
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Heading: "Connection request"
        Text(
            text = if (isBangla) "সংযোগের অনুরোধ" else "Connection request",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle: Real inviter details
        Text(
            text = if (request.senderName.isNotBlank()) {
                if (isBangla) "${request.senderName} ফোর্ট চ্যাটে যুক্ত হতে চান।"
                else "${request.senderName} wants to connect on Fort Chat."
            } else {
                if (isBangla) "আপনার পরিচিত কেউ ফোর্ট চ্যাটে যুক্ত হতে চান।"
                else "Someone you know wants to connect on Fort Chat."
            },
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Structured Info Card matching reference
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x141E40AF))
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(0.5.dp, cardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Row 1: Verified inviter
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(circleIconBg)
                            .border(0.5.dp, circleIconBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isBangla) "যাচাইকৃত প্রেরক" else "Verified inviter",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            ConnectionCardBadge(cardType = request.senderCardType)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBangla)
                                "এই অনুরোধটিতে বিদ্যমান ফোর্ট চ্যাট ব্যবহারকারীর একটি বৈধ আমন্ত্রণ রয়েছে।"
                            else
                                "This request includes a valid invite from an existing Fort Chat user.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Row 2: What you can share
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(circleIconBg)
                            .border(0.5.dp, circleIconBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = null,
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBangla) "আপনি যা শেয়ার করতে পারেন" else "What you can share",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBangla)
                                "কেবল আপনি যা নির্বাচন করেন। আপনার বিদ্যমান চ্যাট, অ্যাক্সেস এবং অবস্থান গোপন থাকে।"
                            else
                                "Only what you choose. Your existing chats, access and location stay private.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Row 3: Duration
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(circleIconBg)
                            .border(0.5.dp, circleIconBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBangla) "স্থায়িত্বকাল" else "Duration",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBangla)
                                "এই সংযোগটি সাময়িক হতে পারে এবং যেকোনো সময় সরানো যেতে পারে।"
                            else
                                "This connection can be temporary and removed at any time.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Sandboxed Message Excerpt (if provided)
                if (request.rawMessageExcerpt.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC))
                            .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "\"${request.rawMessageExcerpt}\"",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                    }
                }

                // Sandboxed Plain-Text Link (if provided)
                if (request.sandboxedLink != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                            .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LinkOff,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isBangla) "স্যান্ডবক্সড লিংক (ট্র্যাকিং নিষ্ক্রিয়)" else "SANDBOXED LINK (TRACKING DISABLED)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = request.sandboxedLink,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action 1: "Accept request" (Primary Royal Blue filled button, >=48dp touch target)
        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = if (isBangla) "অনুরোধ গ্রহণ করুন" else "Accept request",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Action 2: "Decline" (Outlined button, >=48dp touch target)
        OutlinedButton(
            onClick = onDecline,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.2.dp, if (isDark) Color(0xFF3B82F6) else RoyalBluePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = if (isBangla) "প্রত্যাখ্যান করুন" else "Decline",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFF60A5FA) else RoyalBluePrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action 3: "Block" (Subtle destructive action, >=48dp touch target)
        TextButton(
            onClick = onBlock,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Block,
                contentDescription = null,
                tint = RoseDestructive,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isBangla) "ব্লক ও রিপোর্ট করুন" else "Block & Report",
                fontSize = 13.sp,
                color = RoseDestructive,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
