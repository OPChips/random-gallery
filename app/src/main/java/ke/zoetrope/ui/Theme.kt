package ke.zoetrope.ui

import androidx.compose.ui.graphics.Color

/* 暗房色板(与设计稿一致) */
val FilmBlack = Color(0xFF08080A)
val PanelBlack = Color(0xFF101014)
val Safelight = Color(0xFFFF4B2E)
val Tungsten = Color(0xFFE8A33D)
val Paper = Color(0xFFEDE8DF)
val DimText = Color(0xFF8A8578)
val LineDark = Color(0xFF26262E)
val ChipBg = Color(0xFF1A1A20)
val HudBg = Color(0xEE0C0C10)
val ToastBg = Color(0xEE0C0C10)

/** 主题背景:system = 暗房黑,dark = 纯黑(OLED) */
fun ztBg(theme: String?): Color = if (theme == "dark") Color.Black else FilmBlack
