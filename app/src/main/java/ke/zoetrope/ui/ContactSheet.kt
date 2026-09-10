package ke.zoetrope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ke.zoetrope.data.Library
import ke.zoetrope.data.Photo

/** 印样/网格:某文件夹的照片墙。长按进入多选,可批量移入回收站;点单张回大图。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactSheet(
    lib: Library,
    bucketId: String,
    toast: ToastFn,
    onOpenPhoto: (Photo) -> Unit,
    onTrash: (Set<Long>) -> Unit,
    onClose: () -> Unit,
) {
    val photos = remember(lib.photos, lib.hidden, lib.ignored) {
        lib.photos.filter { it.bucketId == bucketId && it.bucketId !in lib.hidden && it.id !in lib.ignored }
    }
    val name = remember(photos) { photos.firstOrNull()?.bucketName ?: "文件夹" }
    var sel by remember { mutableStateOf<Set<Long>>(emptySet()) }

    Column(Modifier.fillMaxSize().background(FilmBlack)) {
        // 头栏(垫状态栏安全区)
        Row(
            Modifier.fillMaxWidth().background(PanelBlack)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sel.isEmpty()) {
                Text(
                    "←", color = DimText, fontSize = 17.sp,
                    modifier = Modifier.clickable { onClose() }.padding(4.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(name, color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "CONTACT SHEET · ${photos.size} FRAMES · 批量管理入口",
                        color = DimText, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.1.em
                    )
                }
            } else {
                Text(
                    "取消", color = DimText, fontSize = 13.sp,
                    modifier = Modifier.clickable { sel = emptySet() }.padding(4.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("已选 ${sel.size}", color = Safelight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.background(Color(0x2EFF4B2E), RoundedCornerShape(999.dp))
                        .border(1.dp, Safelight, RoundedCornerShape(999.dp))
                        .clickable {
                            onTrash(sel.toSet())
                            sel = emptySet()
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) { Text("🗑 删除", color = Safelight, fontSize = 12.sp) }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 4.dp, top = 4.dp, end = 4.dp,
                bottom = 4.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(photos, key = { it.id }) { p ->
                val on = p.id in sel
                val cur = lib.current()?.id == p.id
                Box(
                    Modifier.aspectRatio(1f)
                        .alpha(if (on) 0.85f else 1f)
                        .border(if (on || cur) 2.dp else 0.dp, Safelight)
                        .combinedClickable(
                            onClick = {
                                if (sel.isEmpty()) onOpenPhoto(p) else
                                    sel = if (on) sel - p.id else sel + p.id
                            },
                            onLongClick = { sel = sel + p.id }
                        )
                ) {
                    AsyncImage(p.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (on) {
                        Box(
                            Modifier.padding(5.dp).size(18.dp)
                                .background(Safelight, RoundedCornerShape(999.dp))
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center
                        ) { Text("✓", color = FilmBlack, fontSize = 11.sp) }
                    }
                    if (p.id in lib.favs) {
                        Text(
                            "♥", color = Safelight, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}
