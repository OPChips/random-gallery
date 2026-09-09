package ke.zoetrope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 首启授权屏。denied=true → 「还没有照片权限」+ 去授权(跳系统设置)。 */
@Composable
fun PermScreen(
    denied: Boolean,
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(FilmBlack).padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 图标:54dp 圆底
            Box(
                Modifier.size(54.dp).background(Color(0xFF1A0E0B), RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (denied) "◌" else "◉",
                    color = if (denied) Color(0xFF45454E) else Safelight, fontSize = 24.sp
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (denied) "还没有照片权限" else "走马灯要读你的照片",
                color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (denied) "没有权限,走马灯无图可放。\n可以去系统设置里重新授权。"
                else "只在本机浏览和随机放映,不联网、不上传。\n下一步系统会询问照片权限,请点「允许」。",
                color = DimText, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth()
                    .background(Safelight, RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onGrant() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (denied) "去授权" else "授权,开始翻图",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
            if (!denied) {
                Spacer(Modifier.height(12.dp))
                // 暂不:ghost 描边按钮
                Box(
                    Modifier.border(1.dp, LineDark, RoundedCornerShape(999.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSkip() }
                        .padding(horizontal = 28.dp, vertical = 9.dp)
                ) {
                    Text("暂不", color = DimText, fontSize = 12.sp)
                }
            }
        }
    }
}
