package ke.zoetrope.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 按 Material 官方 SVG 路径数据构建 24dp 视口图标(ui 1.7 移除了 PathBuilder 版 path{},只能走 addPath) */
private fun materialVector(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    ).build()

/** material-icons-core 里没有的图标,路径与 design/index.html 中 SVG 同源 */
val ShuffleIcon: ImageVector = materialVector(
    "Shuffle",
    "M10.59 9.17 5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 " +
        "5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"
)

val PauseIcon: ImageVector = materialVector(
    "Pause",
    "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
)
