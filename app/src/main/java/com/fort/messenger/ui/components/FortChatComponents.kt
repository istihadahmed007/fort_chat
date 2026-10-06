package com.fort.messenger.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.fort.messenger.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.ui.theme.PrivateByDefaultBgDark
import com.fort.messenger.ui.theme.PrivateByDefaultBgLight
import com.fort.messenger.ui.theme.PrivateByDefaultTextDark
import com.fort.messenger.ui.theme.PrivateByDefaultTextLight
import com.fort.messenger.ui.theme.RoyalBluePrimary

import com.fort.messenger.ui.theme.LocalIsDarkTheme

@Composable
fun isAppDarkTheme(): Boolean = LocalIsDarkTheme.current

/**
 * Green "Private by default" chip matching Fort Chat reference design.
 */
@Composable
fun PrivateByDefaultBadge(
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()
    val bgColor = if (isDark) PrivateByDefaultBgDark else PrivateByDefaultBgLight
    val contentColor = if (isDark) PrivateByDefaultTextDark else PrivateByDefaultTextLight

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Lock",
            tint = contentColor,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(3.5.dp))
        Text(
            text = "Private by default",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            letterSpacing = 0.1.sp
        )
    }
}

/**
 * Ice-Blue Liquid-Glass Background Brush applied across Fort Chat screens.
 */
fun Modifier.iceBlueLiquidBackground(isDark: Boolean): Modifier = this.then(
    Modifier.background(
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(
                    Color(0xFF0A0F1D),
                    Color(0xFF0F172A),
                    Color(0xFF0A0F1D)
                )
            } else {
                listOf(
                    Color(0xFFF0F7FF),
                    Color(0xFFE8F1FC),
                    Color(0xFFF8FAFD),
                    Color(0xFFFFFFFF)
                )
            }
        )
    )
)

/**
 * Fort Chat App Logo: Royal-blue shield badge with inner speech bubble matching mockup design.
 */
@Composable
fun FortChatLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val isDark = isAppDarkTheme()

    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(10.dp), spotColor = Color(0x331E40AF)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = "Fort Chat Logo",
            tint = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB),
            modifier = Modifier.size(size)
        )
        Icon(
            imageVector = Icons.Filled.ModeComment,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

/**
 * Hero Illustration for Screen 1: Overlapping translucent speech bubbles and glowing central shield.
 */
@Composable
fun FortChatEmptyHero(
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()
    val ringOuter = if (isDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFE2E8F0).copy(alpha = 0.5f)
    val ringMid = if (isDark) Color(0xFF1E3A5F).copy(alpha = 0.45f) else Color(0xFFDBEAFE).copy(alpha = 0.65f)
    val ringInner = if (isDark) Color(0xFF2563EB).copy(alpha = 0.25f) else Color(0xFFBFDBFE).copy(alpha = 0.55f)

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        // Concentric glow rings
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(ringOuter)
        )
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(CircleShape)
                .background(ringMid)
        )
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(ringInner)
        )

        // Left speech bubble
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 48.dp)
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isDark) Color(0xFF1E293B).copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.75f)
                )
                .border(
                    0.5.dp,
                    if (isDark) Color(0xFF334155) else Color(0xFFDBEAFE),
                    RoundedCornerShape(18.dp)
                )
        )

        // Right speech bubble
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 52.dp)
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isDark) Color(0xFF1E3A5F).copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.85f)
                )
                .border(
                    0.5.dp,
                    if (isDark) Color(0xFF2563EB).copy(alpha = 0.4f) else Color(0xFF93C5FD),
                    RoundedCornerShape(18.dp)
                )
        )

        // Central Shield Icon
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(8.dp, CircleShape, spotColor = RoyalBluePrimary)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

/**
 * Hero Illustration for Screen 2 (Connection Request):
 * Concentric liquid rings with 3D gradient blue shield and white checkmark inside.
 */
@Composable
fun FortConnectionRequestHero(
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()
    val ringOuter = if (isDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFEFF6FF)
    val ringMid = if (isDark) Color(0xFF1E3A5F).copy(alpha = 0.5f) else Color(0xFFDBEAFE)
    val ringInner = if (isDark) Color(0xFF2563EB).copy(alpha = 0.25f) else Color(0xFFBFDBFE).copy(alpha = 0.65f)

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer concentric ring with liquid blur feeling
        Box(
            modifier = Modifier
                .size(176.dp)
                .clip(CircleShape)
                .background(ringOuter)
                .border(0.5.dp, ringMid.copy(alpha = 0.4f), CircleShape)
        )
        // Mid concentric ring
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(CircleShape)
                .background(ringMid.copy(alpha = 0.7f))
        )
        // Inner luminous ring
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(ringInner)
        )

        // Shield in center
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(12.dp, CircleShape, spotColor = Color(0xFF2563EB))
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Verified Request",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Hero Banner for Screen 3 (Conversation Screen):
 * Translucent shield with padlock and private messaging assurance.
 */
@Composable
fun FortPrivateConversationBanner(
    modifier: Modifier = Modifier,
    title: String = "Your conversation is private",
    description: String = "Messages are end-to-end encrypted and visible only to you and the other person."
) {
    val isDark = isAppDarkTheme()

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Translucent glowing shield icon with padlock
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDark) Color(0xFF1E293B).copy(alpha = 0.6f)
                        else Color(0xFFEFF6FF).copy(alpha = 0.85f)
                    )
                    .border(
                        0.5.dp,
                        if (isDark) Color(0xFF3B82F6).copy(alpha = 0.3f) else Color(0xFFBFDBFE),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Encrypted Lock",
                    tint = if (isDark) Color(0xFF93C5FD) else Color(0xFF2563EB).copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Mobile Privacy Showcase Screen 1 Top Bar:
 * Fort Chat Shield Logo + Subtitle "Private people. Brighter conversations." + "Quiet Presence" frosted pill.
 */
@Composable
fun FortChatShowcaseTopBar(
    onOpenPresence: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Ice-blue shield logo with stylized inner fortress motif
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color(0x4038BDF8)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_fort_chat),
                    contentDescription = "Fort Chat Logo",
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "Fort Chat",
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Private people. Brighter conversations.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseMutedText
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Frosted "Quiet Presence" pill badge with green dot
        FortQuietPresencePill(onClick = onOpenPresence)
    }
}

/**
 * "Quiet Presence" frosted pill button with glowing green status indicator.
 */
@Composable
fun FortQuietPresencePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (isDark) com.fort.messenger.ui.theme.QuietPresencePillBgDark else com.fort.messenger.ui.theme.QuietPresencePillBgLight)
            .border(
                0.8.dp,
                if (isDark) com.fort.messenger.ui.theme.QuietPresencePillBorderDark else com.fort.messenger.ui.theme.QuietPresencePillBorderLight,
                RoundedCornerShape(17.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Luminous green online/presence dot
            Box(
                modifier = Modifier
                    .size(7.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .shadow(3.dp, CircleShape, spotColor = Color(0xFF22C55E))
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "Quiet Presence",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) com.fort.messenger.ui.theme.QuietPresenceTextDark else com.fort.messenger.ui.theme.QuietPresenceText
            )
        }
    }
}

/**
 * Mobile Privacy Showcase Screen 1: 3 Horizontal Action Cards
 * [Scan invite] [Create invite] [Find by Fort ID]
 */
@Composable
fun FortChatShowcaseActionCardsRow(
    onScanInvite: () -> Unit,
    onCreateInvite: () -> Unit,
    onFindByFortId: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FortShowcaseActionCard(
            title = "Scan invite",
            subtitle = "Join with a code",
            icon = androidx.compose.material.icons.Icons.Outlined.QrCodeScanner,
            onClick = onScanInvite,
            modifier = Modifier.weight(1f)
        )
        FortShowcaseActionCard(
            title = "Create invite",
            subtitle = "Bring people in",
            icon = androidx.compose.material.icons.Icons.Outlined.Link,
            onClick = onCreateInvite,
            modifier = Modifier.weight(1f)
        )
        FortShowcaseActionCard(
            title = "Find by Fort ID",
            subtitle = "Connect directly",
            icon = androidx.compose.material.icons.Icons.Outlined.PersonAddAlt1,
            onClick = onFindByFortId,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FortShowcaseActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    Box(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0x141E40AF)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isDark) Color(0xFF131D31).copy(alpha = 0.85f)
                else Color.White.copy(alpha = 0.85f)
            )
            .border(
                width = 0.8.dp,
                color = if (isDark) Color(0xFF1E3A5F) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon in translucent circular container
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E3A5F) else Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseMutedText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Round pill button with chevron '>'
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E2B47) else Color(0xFFF1F5F9))
                    .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * Mobile Privacy Showcase Screen 1 Empty State Hero:
 * 3D translucent liquid speech bubbles (cyan & iridescent lavender) with sparkles,
 * "Your chats live here" + subtitle + script quote "Good conversations build brighter days."
 */
@Composable
fun FortChatShowcaseEmptyHero(
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 3D Liquid Dual Speech Bubbles with sparkles matching showcase reference
        Image(
            painter = painterResource(id = R.drawable.hero_3d_bubbles),
            contentDescription = "Your chats live here",
            modifier = Modifier.size(195.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Your chats live here",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Private conversations, real connections.\nStart by inviting someone or finding\nthem with a Fort ID.",
            fontSize = 13.5.sp,
            color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseNavySubtext,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Script cursive accent matching Phone 1
        Text(
            text = "Good conversations\nbuild brighter days.",
            fontSize = 15.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = if (isDark) Color(0xFF93C5FD) else Color(0xFF3B82F6),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * Mobile Privacy Showcase Screen 2 (Knock First):
 * 3D Translucent Glowing Shield with Orbital Light Ring.
 */
@Composable
fun FortKnockFirstOrbitalHero(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.hero_3d_orbital_shield),
            contentDescription = "Knock First Shield",
            modifier = Modifier.size(135.dp)
        )
    }
}

/**
 * Mobile Privacy Showcase Screen 2 (Knock First): Requester Profile Glass Card
 * Contains: "Knock First" title + subtitle, Avatar with verified checkmark, handle, quoted excerpt, padlock privacy box, and 3 stacked buttons.
 */
@Composable
fun FortKnockFirstCard(
    senderDisplayName: String,
    senderHandle: String,
    messageExcerpt: String,
    timestamp: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    // Main White Frosted Card matching Phone 2
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(26.dp), spotColor = Color(0x141E40AF))
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (isDark) Color(0xFF131D31).copy(alpha = 0.92f)
                else Color.White.copy(alpha = 0.92f)
            )
            .border(
                0.8.dp,
                if (isDark) Color(0xFF1E3A5F) else Color(0xFFE2E8F0),
                RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header inside card
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Knock First",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Someone would like to start a conversation\nwith you on Fort Chat.",
                    fontSize = 12.sp,
                    color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseNavySubtext,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 15.sp
                )
            }

            // Inner Requester Profile Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1A2740) else Color(0xFFF8FAFD))
                    .border(0.6.dp, if (isDark) Color(0xFF283A5E) else Color(0xFFE9EEF5), RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Avatar + Name & Handle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(46.dp)) {
                            // Circular photo avatar
                            Image(
                                painter = painterResource(id = R.drawable.avatar_maya_chen),
                                contentDescription = senderDisplayName,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )

                            // Verified badge attached to avatar
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6366F1))
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Verified",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                senderDisplayName.ifBlank { "Maya Chen" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                senderHandle.ifBlank { "@maya.chen" },
                                fontSize = 11.5.sp,
                                color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseMutedText
                            )
                        }
                    }

                    // Quoted Message Bubble
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF131D31) else Color.White)
                            .border(0.5.dp, if (isDark) Color(0xFF253754) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Column {
                            Text(
                                text = messageExcerpt.ifBlank { "Hi! We met at the design summit.\nI'd love to stay in touch here." },
                                fontSize = 12.5.sp,
                                color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = timestamp.ifBlank { "2h ago" },
                                fontSize = 10.5.sp,
                                color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseMutedText
                            )
                        }
                    }
                }
            }

            // Frosted Padlock Privacy Callout Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF132A44) else Color(0xFFEFF6FF))
                    .border(0.8.dp, if (isDark) Color(0xFF1E3A5F) else Color(0xFFDBEAFE), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1E3A5F) else Color(0xFF38BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Padlock",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Your privacy stays in control.",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "They won't be able to message you unless you accept. You can also decline or block at any time.",
                            fontSize = 10.sp,
                            color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseNavySubtext,
                            lineHeight = 13.5.sp
                        )
                    }
                }
            }

            // Action 1: "✓ Accept" (Liquid Cyan pill button with dark navy text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .shadow(3.dp, RoundedCornerShape(21.dp), spotColor = Color(0x3338BDF8))
                    .clip(RoundedCornerShape(21.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFA5F3FC),
                                Color(0xFF67E8F9),
                                Color(0xFF38BDF8)
                            )
                        )
                    )
                    .clickable(onClick = onAccept),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Accept",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }

            // Action 2: "✕ Decline" (Frosted light blue/white pill button)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(
                        if (isDark) Color(0xFF1E293B).copy(alpha = 0.9f)
                        else Color(0xFFF0F9FF).copy(alpha = 0.9f)
                    )
                    .border(
                        0.8.dp,
                        if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD),
                        RoundedCornerShape(21.dp)
                    )
                    .clickable(onClick = onDecline),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else Color(0xFF0F172A),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Decline",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else Color(0xFF0F172A)
                    )
                }
            }

            // Action 3: "⊘ Block" (Rose-tinted frosted glass pill button)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(
                        if (isDark) Color(0xFF2D1520).copy(alpha = 0.9f)
                        else Color(0xFFFFF1F2).copy(alpha = 0.9f)
                    )
                    .border(
                        0.8.dp,
                        if (isDark) Color(0xFF4C1D2C) else Color(0xFFFECDD3),
                        RoundedCornerShape(21.dp)
                    )
                    .clickable(onClick = onBlock),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Block,
                        contentDescription = null,
                        tint = Color(0xFF991B1B),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Block",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF991B1B)
                    )
                }
            }
        }
    }
}

/**
 * Centered floating frosted pill badge: "Messages are end-to-end encrypted".
 */
@Composable
fun FortEncryptedPillBadge(
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF132A44) else Color(0xFFEFF6FF).copy(alpha = 0.9f))
            .border(
                0.8.dp,
                if (isDark) Color(0xFF1E3A5F) else Color(0xFFDBEAFE),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Messages are end-to-end encrypted",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
            )
        }
    }
}

/**
 * Mobile Privacy Showcase Screen 3 Top Bar:
 * Back arrow, round avatar with green online dot, Participant Name + Blue verified badge,
 * Subtitle "Online · Quiet Presence", Video call button, Audio call button, Overflow menu.
 */
@Composable
fun FortConversationTopBar(
    participantName: String,
    avatarEmoji: String,
    onBack: () -> Unit,
    onVideoCall: () -> Unit,
    onAudioCall: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button (<)
        androidx.compose.material3.IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText,
                modifier = Modifier.size(22.dp)
            )
        }

        // Avatar with green online dot
        Box(modifier = Modifier.size(42.dp)) {
            Image(
                painter = painterResource(id = R.drawable.avatar_maya_chen),
                contentDescription = participantName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )

            // Green Online Dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Name + Verified Badge + Presence Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = participantName.ifBlank { "Maya Chen" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Purple/indigo verified checkmark badge matching showcase
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Verified",
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Online • Quiet Presence",
                    fontSize = 11.sp,
                    color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseMutedText
                )
            }
        }

        // Call & Overflow actions in rounded light-blue squircle containers
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF1E2B47) else Color(0xFFEFF6FF))
                .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFDBEAFE), RoundedCornerShape(12.dp))
                .clickable(onClick = onVideoCall),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Videocam,
                contentDescription = "Video Call",
                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF1E2B47) else Color(0xFFEFF6FF))
                .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFDBEAFE), RoundedCornerShape(12.dp))
                .clickable(onClick = onAudioCall),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Call,
                contentDescription = "Audio Call",
                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        androidx.compose.material3.IconButton(
            onClick = onOverflow,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.MoreVert,
                contentDescription = "More Options",
                tint = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseNavySubtext,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Mobile Privacy Showcase Screen 3: Floating Pill Composer
 * (+) [Message securely... 😊 🎙️] [Waveform Squircle] matching Phone 3
 */
@Composable
fun FortConversationPillComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onEmojiToggle: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppDarkTheme()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. (+) Frosted circular attachment button
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(2.dp, CircleShape, spotColor = Color(0x141E40AF))
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.95f))
                .border(0.8.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), CircleShape)
                .clickable(onClick = onAttach),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Add,
                contentDescription = "Attach",
                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                modifier = Modifier.size(20.dp)
            )
        }

        // 2. Middle Frosted Input Pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .shadow(2.dp, RoundedCornerShape(23.dp), spotColor = Color(0x141E40AF))
                .clip(RoundedCornerShape(23.dp))
                .background(
                    if (isDark) Color(0xFF131D31).copy(alpha = 0.95f)
                    else Color.White.copy(alpha = 0.95f)
                )
                .border(
                    0.8.dp,
                    if (isDark) Color(0xFF1E3A5F) else Color(0xFFDBEAFE),
                    RoundedCornerShape(23.dp)
                )
                .padding(start = 14.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Text input
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText
                    ),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2563EB)),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = "Message securely...",
                                fontSize = 13.5.sp,
                                color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else Color(0xFF94A3B8)
                            )
                        }
                        innerTextField()
                    }
                )

                // Emoji smiley button
                androidx.compose.material3.IconButton(
                    onClick = onEmojiToggle,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.SentimentSatisfied,
                        contentDescription = "Emoji",
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Mic button
                androidx.compose.material3.IconButton(
                    onClick = onMic,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Mic,
                        contentDescription = "Voice Message",
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // 3. Right Sky-Blue Squircle Button (Waveform or Send)
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(3.dp, RoundedCornerShape(15.dp), spotColor = Color(0x332563EB))
                .clip(RoundedCornerShape(15.dp))
                .background(
                    if (isDark) Color(0xFF2563EB)
                    else Color(0xFF7DD3FC)
                )
                .clickable {
                    if (text.isNotBlank()) onSend() else onMic()
                },
            contentAlignment = Alignment.Center
        ) {
            if (text.isNotBlank()) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.GraphicEq,
                    contentDescription = "Voice Note Waveform",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

/**
 * Realistic modern mobile status bar (time 9:41, dynamic island, signal, wifi, battery)
 * matching the showcase presentation in the reference image.
 */
@Composable
fun FortDeviceStatusBar(
    modifier: Modifier = Modifier,
    isDark: Boolean = isAppDarkTheme()
) {
    val contentColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "9:41",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
        // Dynamic Island pill
        Box(
            modifier = Modifier
                .width(108.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
        )
        // Cellular, Wifi, Battery
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.width(3.dp).height(4.dp).background(contentColor))
                Box(modifier = Modifier.width(3.dp).height(6.dp).background(contentColor))
                Box(modifier = Modifier.width(3.dp).height(8.dp).background(contentColor))
                Box(modifier = Modifier.width(3.dp).height(10.dp).background(contentColor))
            }
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(10.5.dp)
                    .border(1.dp, contentColor, RoundedCornerShape(3.dp))
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.85f)
                        .background(contentColor, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

/**
 * Rounded horizontal home indicator bar at bottom of mobile frame.
 */
@Composable
fun FortHomeIndicator(
    modifier: Modifier = Modifier,
    isDark: Boolean = isAppDarkTheme()
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(135.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.55f))
        )
    }
}
