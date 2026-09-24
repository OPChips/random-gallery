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
val PauseIcon: ImageVector = materialVector(
    "Pause",
    "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
)
