package com.fort.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.CardType
import com.fort.messenger.ui.theme.PersonaMarketplace
import com.fort.messenger.ui.theme.PersonaPersonal
import com.fort.messenger.ui.theme.PersonaTravel
import com.fort.messenger.ui.theme.PersonaWork

@Composable
fun ConnectionCardBadge(
    cardType: CardType,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (cardType) {
        CardType.PERSONAL -> Triple(Color(0xFFEFF6FF), PersonaPersonal, "Personal")
        CardType.WORK -> Triple(Color(0xFFF0F9FF), PersonaWork, "Work")
        CardType.TRAVEL -> Triple(Color(0xFFF0FDFA), PersonaTravel, "Travel")
        CardType.MARKETPLACE -> Triple(Color(0xFFFEF3C7), PersonaMarketplace, "Market")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp
        )
    }
}
