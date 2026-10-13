package org.beidou.stareye.ui.theme

import androidx.compose.ui.graphics.Color

// —— 深空背景 ——
val DeepSpace = Color(0xFF05070F)
val DeepSpaceLight = Color(0xFF0A0F24)

// —— 星云 ——
val NebulaPurple = Color(0xFF3A2A6B)
val NebulaBlue = Color(0xFF1B3A6B)
val NebulaCyan = Color(0xFF2E6B8A)

// —— 星光 ——
val StarWhite = Color(0xFFEAF6FF)
val StarBlue = Color(0xFF8FD8FF)
val StarGold = Color(0xFFFFD9A0)

// —— 液态玻璃 ——
val GlassTint = Color(0x12FFFFFF)
val GlassStroke = Color(0x38FFFFFF)

/**
 * 北斗七星七颗星的专属强调色（按斗序 1..7）。
 */
val StarAccents = listOf(
    Color(0xFF8FD8FF), // 天枢 蓝
    Color(0xFF7CE0C3), // 天璇 青绿
    Color(0xFFA8A0FF), // 天玑 紫
    Color(0xFFFFC98A), // 天权 金
    Color(0xFF9BD9FF), // 玉衡 冰蓝
    Color(0xFFFF9FC0), // 开阳 粉
    Color(0xFF8FA6FF), // 瑶光 靛
)

/** 按斗序取星位强调色。 */
fun starAccent(order: Int): Color = StarAccents[(order - 1).coerceIn(0, StarAccents.size - 1)]
