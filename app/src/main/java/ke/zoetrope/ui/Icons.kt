package ke.zoetrope.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * material-icons-core 里没有的两个图标,按 Material 官方 SVG 路径自绘:
 * - Shuffle(洗牌钮)——顺带替代字形 ⤨,根治真机 tofu 风险
 * - Pause(HUD 暂停键,PlayArrow 在 core 里有)
 */
val ShuffleIcon: ImageVector = materialIcon(name = "Shuffle") {
    path(fill = SolidColor(Color.Black)) {
        moveTo(10.59f, 9.17f)
        lineTo(5.41f, 4.0f)
        lineTo(4.0f, 5.41f)
        lineTo(9.17f, 10.58f)
        lineTo(10.59f, 9.17f)
        close()
        moveTo(14.5f, 4.0f)
        lineTo(16.54f, 6.04f)
        lineTo(4.0f, 18.59f)
        lineTo(5.41f, 20.0f)
        lineTo(17.96f, 7.46f)
        lineTo(20.0f, 9.5f)
        lineTo(20.0f, 4.0f)
        lineTo(14.5f, 4.0f)
        close()
        moveTo(14.83f, 13.41f)
        lineTo(13.42f, 14.82f)
        lineTo(16.55f, 17.95f)
        lineTo(14.5f, 20.0f)
        lineTo(20.0f, 20.0f)
        lineTo(20.0f, 14.5f)
        lineTo(17.96f, 16.54f)
        lineTo(14.83f, 13.41f)
        close()
    }
}

val PauseIcon: ImageVector = materialIcon(name = "Pause") {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6.0f, 19.0f)
        lineTo(10.0f, 19.0f)
        lineTo(10.0f, 5.0f)
        lineTo(6.0f, 5.0f)
        close()
        moveTo(14.0f, 5.0f)
        lineTo(14.0f, 19.0f)
        lineTo(18.0f, 19.0f)
        lineTo(18.0f, 5.0f)
        close()
    }
}
