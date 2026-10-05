package com.fort.messenger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Green "Private by default" chip matching Fort Chat reference design.
 */
@Composable
fun PrivateByDefaultBadge(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
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
 * Fort Chat App Logo: Royal-blue rounded badge with chat bubble and shield.
 */
@Composable
fun FortChatLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val isDark = isSystemInDarkTheme()
    val gradientBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
        } else {
            listOf(Color(0xFF2563EB), Color(0xFF1E40AF))
        }
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(11.dp), spotColor = Color(0x331E40AF))
            .clip(RoundedCornerShape(11.dp))
            .background(gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ModeComment,
            contentDescription = "Fort Chat Logo",
            tint = Color.White,
            modifier = Modifier.size(size * 0.58f)
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
    val isDark = isSystemInDarkTheme()
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
    val isDark = isSystemInDarkTheme()
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
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
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
