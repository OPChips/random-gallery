@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ke.zoetrope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ke.zoetrope.data.Library

/** 左侧抽屉:收藏 / 全库 / 文件夹(含隐藏恢复、印样入口)/ 已忽略 / 设置 */
@Composable
fun DrawerPanel(
    lib: Library,
    toast: ToastFn,
    onClose: () -> Unit,
    onOpenGrid: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val folders = remember(lib.photos, lib.hidden) { lib.folders() }
    Column(
        Modifier.fillMaxHeight().fillMaxWidth(0.79f)
            .background(PanelBlack)
            .padding(top = 58.dp, bottom = 12.dp)
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) {
            Text(
                "⟨ FOLDERS / ",
                color = DimText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.14.em
            )
            Text(
                "侧滑抽屉",
                color = Safelight, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.14.em,
                fontWeight = FontWeight.Bold
            )
            Text(
                " ⟩",
                color = DimText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.14.em
            )
        }

        // 收藏行
        DrawerRow(
            onClick = { lib.setScope("fav"); onClose() },
            active = lib.scope == "fav"
        ) {
            Box(
                Modifier.size(44.dp).background(Color(0xFF1A0E0B), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("♥", color = Safelight, fontSize = 17.sp) }
            RowText("收藏", "FAVORITES")
            Spacer(Modifier.weight(1f))
            CountBadge(lib.favs.size)
        }
        // 全库行
        DrawerRow(
            onClick = { lib.setScope("all"); onClose() },
            active = lib.scope == "all"
        ) {
            Box(
                Modifier.size(44.dp).background(Color(0xFF15151A), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("ALL", color = DimText, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
            RowText("全库", "EVERYTHING")
            Spacer(Modifier.weight(1f))
            CountBadge(lib.photos.count { it.bucketId !in lib.hidden })
        }

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)
                .height(1.dp).background(LineDark)
        )

        // 文件夹行
        folders.forEach { f ->
            val hid = f.bucketId in lib.hidden
            DrawerRow(
                onClick = {
                    if (hid) toast("该文件夹已隐藏,点眼睛恢复", null, null)
                    else { lib.setScope("bucket:${f.bucketId}"); onClose() }
                },
                active = lib.scope == "bucket:${f.bucketId}",
                extraAlpha = if (hid) 0.45f else 1f
            ) {
                Box(
                    Modifier.size(44.dp).background(ChipBg, RoundedCornerShape(8.dp))
                ) {
                    f.cover?.let {
                        AsyncImage(
                            it.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            colorFilter = if (hid) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
                        )
                    }
                }
                Column {
                    Text(
                        f.name, color = Paper, fontSize = 13.5.sp,
                        textDecoration = if (hid) TextDecoration.LineThrough else null
                    )
                    val sub = f.cover?.path?.substringBeforeLast('/')?.uppercase() ?: ""
                    if (sub.isNotEmpty())
                        Text(sub, color = DimText, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.08.em)
                }
                Spacer(Modifier.weight(1f))
                if (hid) {
                    Box(
                        Modifier.size(36.dp).clickable {
                            lib.setHidden(f.bucketId, false)
                            toast("已恢复文件夹「${f.name}」", null, null)
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("⊘", color = Safelight, fontSize = 17.sp) }
                    CountBadge(f.count, dim = true)
                } else {
                    CountBadge(f.count)
                    Box(
                        Modifier.size(36.dp).clickable { onOpenGrid(f.bucketId) },
                        contentAlignment = Alignment.Center
                    ) { Text("⊞", color = DimText, fontSize = 17.sp) }
                }
            }
        }

        // 已忽略(单张不再显示)
        if (lib.ignored.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            DrawerRow(onClick = { expanded = !expanded }, active = false) {
                Box(
                    Modifier.size(44.dp).background(ChipBg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("◌", color = Color(0xFF45454E), fontSize = 15.sp) }
                RowText("已忽略", "点缩略图恢复显示")
                Spacer(Modifier.weight(1f))
                CountBadge(lib.ignored.size)
            }
            if (expanded) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ignoredPhotos = lib.photos.filter { it.id in lib.ignored }
                    ignoredPhotos.forEach { p ->
                        Box(
                            Modifier.size(52.dp).alpha(0.55f)
                                .background(ChipBg, RoundedCornerShape(8.dp))
                                .clickable {
                                    lib.setIgnored(p.id, false)
                                    toast("已恢复显示", null, null)
                                }
                        ) {
                            AsyncImage(p.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
        }

        // 设置行(无边框文字行,固定贴底)
        Row(
            Modifier.fillMaxWidth()
                .clickable { onClose(); onOpenSettings() }
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚙", color = Paper, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text("设置", color = Paper, fontSize = 13.5.sp)
            Spacer(Modifier.weight(1f))
            Text("SETTINGS · PREFS", color = DimText, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.1.em)
        }
        Text(
            "HIDE = 移出随机池,文件不动",
            color = Color(0xFF5A5560), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

@Composable
private fun DrawerRow(onClick: () -> Unit, active: Boolean, extraAlpha: Float = 1f, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (active) Color(0x14FFFFFF) else Color.Transparent)
            .alpha(extraAlpha)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (active) Box(Modifier.width(3.dp).height(30.dp).background(Safelight))
        content()
    }
}

@Composable
private fun RowText(name: String, sub: String) {
    Column {
        Text(name, color = Paper, fontSize = 13.5.sp)
        if (sub.isNotEmpty())
            Text(sub, color = DimText, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.08.em)
    }
}

@Composable
private fun CountBadge(n: Int, dim: Boolean = false) {
    Text(
        "$n",
        color = if (dim) DimText else Paper.copy(alpha = 0.7f),
        fontSize = 10.sp, fontFamily = FontFamily.Monospace
    )
}
