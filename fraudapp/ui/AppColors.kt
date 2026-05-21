package com.example.fraudapp.ui

import androidx.compose.ui.graphics.Color

object AppColors {
    val BgDeep        = Color(0xFF0D1117)
    val Surface1      = Color(0xFF161B22)
    val Surface2      = Color(0xFF21262D)
    val Surface3      = Color(0xFF2D333B)
    val Border        = Color(0xFF30363D)
    val BorderFocused = Color(0xFF58A6FF)

    val TextPrimary   = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val TextMuted     = Color(0xFF484F58)

    val AccentBlue    = Color(0xFF58A6FF)
    val AccentGreen   = Color(0xFF3FB950)
    val AccentAmber   = Color(0xFFD29922)
    val AccentRed     = Color(0xFFF85149)
    val AccentPurple  = Color(0xFFBC8CFF)

    fun riskColor(score: Double) = when {
        score < 30 -> AccentGreen
        score < 60 -> AccentAmber
        score < 80 -> AccentRed
        else       -> Color(0xFFFF6B6B)
    }

    fun decisionColors(decision: String): Pair<Color, Color> = when (decision.lowercase()) {
        "allow", "verified" -> AccentGreen.copy(alpha = 0.15f) to AccentGreen
        "verify"            -> AccentAmber.copy(alpha = 0.15f) to AccentAmber
        "block"             -> AccentRed.copy(alpha   = 0.15f) to AccentRed
        else                -> AccentPurple.copy(alpha = 0.15f) to AccentPurple
    }
}
