package ke.zoetrope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import ke.zoetrope.data.Library
import ke.zoetrope.data.Photo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ── 通用行组件 ── */

/** 设置行:左标题 + 右控件(与设计稿 set-row 一致,无英文副标题) */
@Composable
private fun SheetRow(
    title: String,
    right: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Paper, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        right()
    }
}

/** 分段选择:选中 = 纸白底黑字(与设计稿 seg/chip.on 一致) */
@Composable
private fun Seg(opts: List<Pair<String, String>>, cur: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        opts.forEach { (v, label) ->
            val on = v == cur
            Box(
                Modifier
                    .background(if (on) Paper else ChipBg, RoundedCornerShape(8.dp))
                    .border(1.dp, if (on) Paper else LineDark, RoundedCornerShape(8.dp))
                    .clickable { onPick(v) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(label, color = if (on) Color(0xFF111111) else DimText, fontSize = 10.5.sp)
            }
        }
    }
}

/** 开关:on = 实心红底 + 白 knob(与设计稿 .sw.on 一致) */
@Composable
private fun Switch(on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.width(40.dp).height(22.dp)
            .background(if (on) Safelight else Color(0xFF26262E), RoundedCornerShape(999.dp))
            .clickable { onToggle() }
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier.size(18.dp).background(if (on) Color.White else Color(0xFF55555F), RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)
            .height(1.dp).background(LineDark)
    )
}

/** ── 设置面板 ── */
@Composable
fun SettingsSheet(
    lib: Library,
    toast: ToastFn,
    onReplayPerm: () -> Unit,
    onClose: () -> Unit,
) {
    val folders = remember(lib.photos) { lib.folders() }
    var startupDlg by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.76f)
            .background(PanelBlack)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        // 抓手条
        Box(
            Modifier.padding(top = 6.dp, bottom = 2.dp).size(width = 34.dp, height = 4.dp)
                .background(Color(0xFF33333D), RoundedCornerShape(999.dp))
                .align(Alignment.CenterHorizontally)
        )
        // 面板头
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设置", color = Paper, fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "✕", color = DimText, fontSize = 15.sp,
                modifier = Modifier.clickable { onClose() }.padding(6.dp)
            )
        }

        SheetRow(
            title = "启动首图",
            right = {
                Text(
                    startupLabel(lib.startup), color = Tungsten, fontSize = 12.sp,
                    modifier = Modifier.clickable { startupDlg = true }.padding(4.dp)
                )
            }
        )
        Divider()
        SheetRow(
            title = "启动自动放映",
            right = {
                Switch(
                    on = lib.autoplay,
                    onToggle = {
                        val v = !lib.autoplay
                        lib.setAutoplay(v)
                        if (v && !lib.playing) lib.startPlay()       // 设计稿:切 on 立即开播
                        if (!v && lib.playing) lib.stopPlay()        // 切 off 立即停
                        toast("⚙ 启动自动放映 已保存", null, null)
                    }
                )
            }
        )
        Divider()
        SheetRow(
            title = "播完一轮",
            right = {
                Seg(
                    listOf("reshuffle" to "重洗继续", "stop" to "停止"),
                    lib.loop
                ) { lib.setLoop(it); toast("⚙ 播完行为 已保存", null, null) }
            }
        )
        Divider()
        SheetRow(
            title = "切图过渡",
            right = {
                Seg(
                    listOf("fade" to "淡入", "slide" to "位移", "none" to "无"),
                    lib.transition
                ) { lib.setTransition(it); toast("⚙ 切图过渡 已保存", null, null) }
            }
        )
        Divider()
        SheetRow(
            title = "信息角标",
            right = {
                Switch(on = lib.caption, onToggle = {
                    lib.setCaption(!lib.caption); toast("⚙ 信息角标 已保存", null, null)
                })
            }
        )
        Divider()
        SheetRow(
            title = "进度细线",
            right = {
                Switch(on = lib.edgebar, onToggle = {
                    lib.setEdgebar(!lib.edgebar); toast("⚙ 进度细线 已保存", null, null)
                })
            }
        )
        Divider()
        SheetRow(
            title = "主题",
            right = {
                Seg(
                    listOf("system" to "跟随系统", "dark" to "纯黑"),
                    lib.theme
                ) { lib.setTheme(it); toast("⚙ 主题 已保存", null, null) }
            }
        )
        Divider()
        SheetRow(
            title = "关于",
            right = {
                Text(
                    "走马灯 ZT-01 · v0.1.0",
                    color = DimText, fontSize = 10.sp,
                    modifier = Modifier.clickable { onClose(); onReplayPerm() }.padding(4.dp)
                )
            }
        )
        Text(
            "HIDE = 移出随机池,文件不动",
            color = Color(0xFF5A5560), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
        )
    }

    // 启动首图对话框
    if (startupDlg) {
        DialogShell(onDismiss = { startupDlg = false }) {
            Text("启动首图", color = Paper, fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("OPENING SHOT", color = DimText, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.14.em)
            Spacer(Modifier.height(14.dp))
            DialogOption("接着上次看", "LAST SEEN", lib.startup == "last") {
                lib.setStartup("last"); toast("⚙ 启动首图 已保存", null, null); startupDlg = false
            }
            DialogOption("随机一张", "RANDOM", lib.startup == "random") {
                lib.setStartup("random"); toast("⚙ 启动首图 已保存", null, null); startupDlg = false
            }
            DialogOption("固定文件夹", "FIXED FOLDER", lib.startup == "fixed") {
                lib.setStartup("fixed"); toast("⚙ 启动首图 已保存", null, null)
            }
            DialogOption("最新一张", "NEWEST", lib.startup == "newest") {
                lib.setStartup("newest"); toast("⚙ 启动首图 已保存", null, null); startupDlg = false
            }
            // 固定文件夹子选择
            if (lib.startup == "fixed") {
                Spacer(Modifier.height(8.dp))
                Text("选文件夹:", color = DimText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                ) {
                    folders.forEach { f ->
                        Text(
                            (if (lib.fixedBucket() == f.bucketId) "◉ " else "○ ") + f.name,
                            color = if (lib.fixedBucket() == f.bucketId) Safelight else Paper,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    lib.setFixedBucket(f.bucketId); lib.setStartup("fixed")
                                    toast("⚙ 固定文件夹 已保存", null, null)
                                }
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun startupLabel(v: String?) = when (v) {
    "last" -> "接着上次看"
    "random" -> "随机一张"
    "newest" -> "最新一张"
    "fixed" -> "固定文件夹"
    else -> "接着上次看"
}

@Composable
private fun DialogOption(label: String, tag: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (on) Paper else ChipBg, RoundedCornerShape(9.dp))
            .border(1.dp, if (on) Paper else LineDark, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (on) Color(0xFF111111) else Paper, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(tag, color = if (on) Color(0xFF111111).copy(alpha = 0.6f) else DimText,
            fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.1.em)
    }
    Spacer(Modifier.height(6.dp))
}

/** ── EXIF 面板:标题即文件名 + 红色 EXIF 标签,路径金色 ── */
@Composable
fun ExifSheet(photo: Photo, folderName: String, isFav: Boolean, onClose: () -> Unit) {
    var exifLines by remember(photo.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(photo.id) {
        val lines = mutableListOf<Pair<String, String>>()
        try {
            val s = photo.path
            if (s.isNotEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.io.FileInputStream(s).use {
                        val ex = ExifInterface(it)
                        val iso = ex.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                        val f = ex.getAttribute(ExifInterface.TAG_F_NUMBER)
                        val exp = ex.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        val model = ex.getAttribute(ExifInterface.TAG_MODEL)
                        if (model != null) lines.add("机身" to model)
                        if (iso != null) lines.add("ISO" to iso)
                        if (f != null) lines.add("光圈" to "f/$f")
                        if (exp != null) lines.add("快门" to "${exp}s")
                    }
                }
            }
        } catch (_: Exception) { }
        exifLines = lines
    }

    Column(
        Modifier.fillMaxWidth().background(PanelBlack)
            .verticalScroll(rememberScrollState())
            .padding(top = 10.dp, bottom = 30.dp)
    ) {
        // 抓手条
        Box(
            Modifier.padding(top = 6.dp, bottom = 4.dp).size(width = 34.dp, height = 4.dp)
                .background(Color(0xFF33333D), RoundedCornerShape(999.dp))
                .align(Alignment.CenterHorizontally)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                photo.name, color = Paper, fontSize = 14.sp,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("EXIF", color = Safelight, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.14.em)
            Spacer(Modifier.width(12.dp))
            Text(
                "✕", color = DimText, fontSize = 15.sp,
                modifier = Modifier.clickable { onClose() }.padding(6.dp)
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)
                .height(1.dp).background(LineDark)
        )

        ExifRow("拍摄时间", fmtDate(photo.dateTaken))
        ExifRow("分辨率", "${photo.width} × ${photo.height}")
        exifLines.forEach { (k, v) -> ExifRow(k, v) }
        ExifRow("文件夹", folderName)
        ExifRow("收藏", if (isFav) "♥ 是" else "否")
        ExifRow("路径", photo.path.ifEmpty { "(MediaStore)" }, gold = true)
    }
}

@Composable
private fun ExifRow(k: String, v: String, gold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(k, color = DimText, fontSize = 11.sp, modifier = Modifier.width(76.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            v, color = if (gold) Tungsten else Paper,
            fontSize = 11.sp, fontFamily = FontFamily.Monospace
        )
    }
}

private fun fmtDate(ts: Long): String =
    SimpleDateFormat("yyyy:MM:dd HH:mm", Locale.getDefault()).format(Date(ts))
