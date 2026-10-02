package com.fort.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.PassDurationType
import com.fort.messenger.ui.theme.AmberWarning
import com.fort.messenger.ui.theme.AmberWarningContainer
import com.fort.messenger.ui.theme.IceBlueBorder
import com.fort.messenger.ui.theme.RoyalBluePrimary

@Composable
fun PassCountdownChip(
    timeRemaining: String,
    passType: PassDurationType,
    modifier: Modifier = Modifier
) {
    val isExpiringSoon = timeRemaining.contains("h", ignoreCase = true) && !timeRemaining.contains("d", ignoreCase = true)
    val bgColor = if (isExpiringSoon) AmberWarningContainer else Color(0xFFF1F5F9)
    val textColor = if (isExpiringSoon) AmberWarning else Color(0xFF475569)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = timeRemaining,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 12.sp
            )
        }
    }
}
