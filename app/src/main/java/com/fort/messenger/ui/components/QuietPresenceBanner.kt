package com.fort.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.MoodRingState
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.viewmodel.AppLanguage

@Composable
fun QuietPresenceBanner(
    moodState: MoodRingState?,
    onOpenMoodPicker: () -> Unit,
    language: AppLanguage = AppLanguage.ENGLISH,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val isBangla = language == AppLanguage.BANGLA
    val cardBg = if (isDark) Color(0xFF131D31) else Color(0xFFEFF6FF)
    val cardBorder = if (isDark) Color(0xFF1E3A5F) else Color(0xFFDBEAFE)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp), spotColor = Color(0x141E40AF))
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(0.5.dp, cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = if (isBangla) "শান্ত উপস্থিতি পরিবর্তন করুন" else "Edit Quiet Presence") { onOpenMoodPicker() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E293B) else Color.White)
                    .border(0.5.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = moodState?.emotion?.emoji ?: "🛡️",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (moodState != null) {
                            if (isBangla) "শান্ত উপস্থিতি: ${moodState.emotion.title}" else "Quiet Presence: ${moodState.emotion.title}"
                        } else {
                            if (isBangla) "শান্ত উপস্থিতি সেট করুন" else "Set Your Quiet Presence"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoyalBluePrimary
                    )
                    if (moodState?.whatINeed != null) {
                        Text(
                            text = " • ${moodState.whatINeed.label}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (moodState != null) {
                        if (isBangla) {
                            "শ্রোতা: ${moodState.sharingCircleName} • মেয়াদ: ${moodState.remainingTimeString}"
                        } else {
                            "Audience: ${moodState.sharingCircleName} • Expires: ${moodState.remainingTimeString}"
                        }
                    } else {
                        if (isBangla) {
                            "শ্রোতা ও মেয়াদ নিয়ন্ত্রিত সংবেদনশীল সীমানা"
                        } else {
                            "Audience & expiry-controlled emotional boundaries"
                        }
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = if (isBangla) "উপস্থিতি সম্পাদনা" else "Edit Presence",
                tint = RoyalBluePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
