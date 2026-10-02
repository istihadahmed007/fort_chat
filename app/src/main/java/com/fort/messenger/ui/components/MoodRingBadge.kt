package com.fort.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 20x20dp micro-badge anchored at avatar lower-right perimeter.
 * Adheres strictly to PRD Section 4.3: does not obscure facial features or avatar focus.
 */
@Composable
fun MoodRingBadge(
    moodEmoji: String,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = moodEmoji,
            fontSize = 11.sp,
            lineHeight = 11.sp
        )
    }
}
