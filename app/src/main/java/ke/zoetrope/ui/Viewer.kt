package ke.zoetrope.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ke.zoetrope.data.Library
import ke.zoetrope.data.Photo
import ke.zoetrope.data.SKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** toast 回调:msg + 可选动作(撤销等) */
typealias ToastFn = (String, String?, (() -> Unit)?) -> Unit

@Composable
fun ViewerScreen(
    lib: Library,
    toast: ToastFn,
    onDrawer: () -> Unit,
    onGrid: (String) -> Unit,
    onSettings: () -> Unit,
    onExif: () -> Unit,
    onTrash: () -> Unit,
) {
    val pool by remember { derivedStateOf { lib.pool() } }
    val current by remember { derivedStateOf { lib.current() } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 系统栏安全区(targetSdk 35 强制全面屏,交互元素要垫到手势条/刘海之外)
    val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // ── 布局/手势状态 ──
    var screenW by remember { mutableStateOf(1f) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(OffsetZero) }
    var dragX by remember { mutableStateOf(0f) }

    // ── 过渡:双层图交叉 ──
    var front by remember { mutableStateOf<Photo?>(null) }
    var back by remember { mutableStateOf<Photo?>(null) }
    val trans = remember { Animatable(1f) }

    // ── 禅模式(看图/放映都默认沉浸,轻点呼出控制,3s 无操作再收起)──
    var zen by remember { mutableStateOf(true) }
    var zenToasted by remember { mutableStateOf(false) }
    var lastSwipeAt by remember { mutableStateOf(0L) }   // 刚滑动切图,350ms 内不算轻点

    // ── 放映计时 ──
    val prog = remember { Animatable(0f) }

    val currentId = current?.id
    // 当前张变化 → 播放过渡动画
    LaunchedEffect(currentId) {
        val p = current ?: return@LaunchedEffect
        if (front?.id == p.id) return@LaunchedEffect
        if (lib.transition == "none") { back = null; front = p; return@LaunchedEffect }
        back = front
        front = p
        trans.snapTo(0f)
        trans.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        back = null
    }
    // 记住最后看的
    LaunchedEffect(currentId) { lib.saveLast(current) }
    // 记录最近浏览的文件夹(设置页"固定文件夹"列表按此排序)
    LaunchedEffect(currentId) { lib.recordBucketVisit(current?.bucketId) }

    // 放映计时 + 进度(细线):暂停时冻结,恢复从断点续播
    var resumeFrom by remember { mutableStateOf(0f) }
    LaunchedEffect(lib.playing, lib.paused, lib.speed, lib.queue, lib.qp) {
        if (lib.playing && !lib.paused && lib.queue.isNotEmpty()) {
            try {
                val from = resumeFrom
                resumeFrom = 0f
                prog.snapTo(from)
                prog.animateTo(1f, tween(((1f - from) * lib.speed * 1000).toInt().coerceAtLeast(1), easing = LinearEasing))
                lib.advance(1)
            } catch (e: CancellationException) {
                if (lib.paused) resumeFrom = prog.value   // 被暂停打断:记住断点
                throw e
            }
        }
    }
    // 禅模式:放映中 3s 无操作自动沉浸;看图态轻点开关控制条,不自动收起
    LaunchedEffect(lib.playing, lib.paused, lib.loaded) {
        when {
            lib.playing && lib.pendingAuto -> { lib.pendingAuto = false; zen = true }
            lib.playing && lib.paused -> zen = false
            lib.playing -> {
                if (pool.isEmpty()) zen = false
                else {
                    delay(3000)
                    zen = true
                    if (!zenToasted) { zenToasted = true; toast("轻点屏幕 · 呼出控制", null, null) }
                }
            }
            lib.loaded && pool.isEmpty() -> zen = false   // 看图态池空:留着抽屉入口
            // 看图态有照片:zen 不动,由轻点开关(启动默认沉浸)
        }
    }

    // ── 交互自动暂停:缩放/长按走 pauseForInteraction(autoPaused),结束 autoResume 自动恢复 ──
    // 面板按钮与轻点图片走 pauseManual(手动暂停,保持到用户再操作)
    // 缩放查看时暂停,缩回 1x 自动恢复
    LaunchedEffect(scale) {
        if (scale > 1f) lib.pauseForInteraction() else if (scale == 1f) lib.autoResume()
    }

    fun page(dir: Int) {
        scale = 1f; offset = OffsetZero; dragX = 0f
        if (lib.playing) lib.advance(dir) else lib.pageManual(dir)
    }

    val tapHandler = { pos: androidx.compose.ui.geometry.Offset ->
        val swiped = System.currentTimeMillis() - lastSwipeAt < 350
        when {
            swiped -> {}
            pos.x < screenW * 0.2f -> page(-1)
            pos.x > screenW * 0.8f -> page(1)
            lib.playing -> { lib.togglePause(); zen = lib.paused }   // 点中间 = 暂停弹面板 / 恢复收面板
            else -> zen = !zen   // 看图态:轻点开/关控制条,不自动收起
        }
    }

    Box(
        Modifier.fillMaxSize().background(ztBg(lib.theme))
            .onSizeChanged { screenW = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapHandler(it) },
                    onDoubleTap = { pos ->
                        if (scale > 1f) { scale = 1f; offset = OffsetZero }
                        else {
                            scale = 2.5f
                            offset = androidx.compose.ui.geometry.Offset(
                                (size.width / 2f - pos.x) * 1.2f,
                                (size.height / 2f - pos.y) * 1.2f
                            )
                        }
                    },
                    onLongPress = {
                        lib.pauseForInteraction()   // 忽略期间冻结计时
                        current?.let {
                            lib.setIgnored(it.id, true)
                            if (lib.playing) lib.buildQueue(false)
                            else lib.setIdx(minOf(lib.idx, (lib.pool().size - 1).coerceAtLeast(0)))
                            lib.autoResume()
                            toast("◌ 已不再显示这张 · 文件不动", "撤销") {
                                lib.setIgnored(it.id, false)
                                toast("已恢复显示", null, null)
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    dragX = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val z = event.calculateZoom()
                            if (z != 1f) {
                                val ns = (scale * z).coerceIn(1f, 5f)
                                if (ns == 1f) offset = OffsetZero
                                scale = ns
                            }
                            if (scale > 1f) {
                                val pan = event.calculatePan()
                                offset = androidx.compose.ui.geometry.Offset(offset.x + pan.x, offset.y + pan.y)
                            }
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            if (scale > 1f) {
                                offset = androidx.compose.ui.geometry.Offset(offset.x + change.positionChange().x, offset.y + change.positionChange().y)
                                change.consume()
                            } else {
                                dragX += change.positionChange().x
                            }
                        }
                    }
                    if (scale == 1f && abs(dragX) > 42 * density.density) {
                        lastSwipeAt = System.currentTimeMillis()
                        page(if (dragX < 0) 1 else -1)
                    }
                    dragX = 0f
                }
            }
    ) {
        // ── 照片层(带缩放/拖拽变换 + 切图过渡)──
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x + dragX * 0.35f
                translationY = offset.y
            },
            contentAlignment = Alignment.Center
        ) {
            back?.let { b ->
                AsyncImage(b.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            front?.let { f ->
                AsyncImage(
                    f.uri, null,
                    Modifier.fillMaxSize().graphicsLayer {
                        if (back == null) {
                            alpha = 1f
                            translationX = 0f; scaleX = 1f; scaleY = 1f
                        } else {
                            val t = 1f - trans.value
                            alpha = trans.value
                            when (lib.transition) {
                                "slide" -> translationX = 22.dp.toPx() * t
                                "fade" -> { val s = 0.965f + 0.035f * trans.value; scaleX = s; scaleY = s }
                            }
                        }
                    },
                    contentScale = ContentScale.Fit
                )
            }
        }

        // ── 空状态 ──
        if (pool.isEmpty()) {
            EmptyState(lib, onDrawer)
        }

        // ── 顶栏 ──
        if (!zen) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 状态点:播放中红色呼吸,否则灰
                    val blink = rememberInfiniteTransition()
                    val dotA by blink.animateFloat(
                        initialValue = 1f, targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse)
                    )
                    Box(Modifier.size(6.dp).background(
                        when {
                            lib.playing && !lib.paused -> Safelight.copy(alpha = dotA)   // 放映中:呼吸
                            lib.playing -> Safelight.copy(alpha = 0.45f)                 // 暂停:实心暗红
                            else -> DimText
                        },
                        RoundedCornerShape(3.dp)
                    ))
                    Spacer(Modifier.width(7.dp))
                    Text(lib.scopeName(), color = Color(0xFFDDDDDD), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Text(
                    counterText(lib, pool.size),
                    color = Color(0xFF999999), fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.08.em
                )
            }
        }

        // ── 信息角标(zen 中仍显示) ──
        if (lib.caption && current != null) {
            Text(
                "${fmtDate(current!!.dateTaken)} · ${current!!.name}",
                color = Paper.copy(alpha = 0.75f),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 104.dp + navPad)
            )
        }

        // ── 底栏 ──
        if (!zen) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Icons.Filled.Menu, "打开抽屉") { lib.pauseManual(); onDrawer() }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeartBtn(faved = current != null && current!!.id in lib.favs) {
                        current?.let {
                            lib.toggleFav(it.id)
                            toast(if (it.id in lib.favs) "♥ 已加入收藏" else "已取消收藏", null, null)
                        }
                    }
                    IconBtn(Icons.Filled.Info, "图片信息") { lib.pauseManual(); onExif() }
                    IconBtn(Icons.Filled.Delete, "删除") { lib.pauseManual(); onTrash() }
                }
                // 放映钮:三态 ▶ 开始 / ⏸ 放映中 / ▶ 暂停中(再点恢复)
                IconBtn(
                    if (lib.playing && !lib.paused) PauseIcon else Icons.Filled.PlayArrow,
                    when {
                        !lib.playing -> "开始放映"
                        lib.paused -> "恢复放映"
                        else -> "暂停放映"
                    },
                    tint = Safelight
                ) {
                    when {
                        !lib.playing -> {
                            if (!lib.startPlay()) toast("这个范围没有照片", null, null)
                            else toast("◍ 随机幻灯片 · ${lib.scopeName()} ${lib.queue.size} 张", null, null)
                        }
                        lib.paused -> { lib.togglePause(); zen = true }   // 恢复 + 收起面板
                        else -> lib.togglePause()
                    }
                }
            }
        }

        // ── 进度细线(垫在导航条安全区上方)──
        if (lib.playing && lib.edgebar) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(prog.value)
                    .padding(bottom = navPad)
                    .height(2.dp).background(Color(0x26FFFFFF))
            )
        }
    }
}

private val OffsetZero = androidx.compose.ui.geometry.Offset.Zero

private fun counterText(lib: Library, poolSize: Int): String {
    val pos = if (lib.playing) lib.qp else lib.idx
    val total = if (lib.playing) lib.queue.size else poolSize
    val cur = (if (lib.playing) lib.qp else lib.idx) + 1
    return "%02d / %02d".format(cur, total.coerceAtLeast(0))
}

private fun fmtDate(ts: Long): String =
    SimpleDateFormat("yyyy:MM:dd HH:mm", Locale.getDefault()).format(Date(ts))

/** 无边框图标按钮(Google 相册风):48dp 触摸区,矢量图标居中 */
@Composable
private fun IconBtn(
    icon: ImageVector,
    contentDesc: String,
    tint: Color = Paper.copy(alpha = 0.85f),
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun HeartBtn(faved: Boolean, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    var popTick by remember { mutableStateOf(0) }   // 只在点按时心跳,翻页到已收藏图不触发
    LaunchedEffect(popTick) {
        if (popTick > 0) {
            pop.animateTo(1.35f, tween(120, easing = FastOutSlowInEasing))
            pop.animateTo(0.92f, tween(90))
            pop.animateTo(1f, tween(90))
        }
    }
    Box(
        Modifier.size(48.dp).clickable { popTick++; onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (faved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (faved) "取消收藏" else "收藏",
            tint = if (faved) Safelight else DimText,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
        )
    }
}

/** 空状态(收藏为空 / 范围无照片) */
@Composable
private fun EmptyState(lib: Library, onDrawer: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ztBg(lib.theme)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◌", color = Color(0xFF45454E), fontSize = 32.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (lib.scope == "fav") "收藏还是空的" else "这个范围没有照片",
                color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (lib.scope == "fav") "看图时点 ♥ 收藏喜欢的照片\n收藏夹本身就是一个随机范围"
                else "换个文件夹,或在抽屉里恢复隐藏的文件夹",
                color = DimText, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.border(1.dp, Safelight, RoundedCornerShape(999.dp))
                    .clickable {
                        if (lib.scope == "fav") lib.setScope("all") else onDrawer()
                    }
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text(
                    if (lib.scope == "fav") "回全库看看" else "打开抽屉",
                    color = Safelight, fontSize = 13.sp
                )
            }
        }
    }
}
