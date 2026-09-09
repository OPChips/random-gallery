package ke.zoetrope

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ke.zoetrope.data.Library
import ke.zoetrope.data.Prefs
import ke.zoetrope.data.SKeys
import ke.zoetrope.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZoetropeApp() }
    }
}

/** 遮罩层状态 */
private enum class Overlay { None, Drawer, Grid, Settings, Exif, Perm }

@Composable
fun ZoetropeApp() {
    val activity = LocalContext.current as Activity
    val prefs = remember { Prefs(activity) }
    val lib = remember { Library(activity, prefs) }
    val scope = rememberCoroutineScope()

    // ── 权限 ──
    fun hasReadPerm(): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED) return true
        @Suppress("DEPRECATION")
        return Build.VERSION.SDK_INT < 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasReadPerm()) }
    var denied by remember { mutableStateOf(false) }
    var askedOnce by remember { mutableStateOf(prefs.getBool(SKeys.PERM_ASKED, false)) }
    var partialNotice by remember { mutableStateOf(false) }   // 部分授权(仅已选照片)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        var partialGrant = false
        granted = if (Build.VERSION.SDK_INT >= 33) {
            val full = result[Manifest.permission.READ_MEDIA_IMAGES] == true
            val part = result[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
            partialGrant = part && !full
            full || part
        } else {
            @Suppress("DEPRECATION")
            result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        denied = !granted
        askedOnce = true
        prefs.putBool(SKeys.PERM_ASKED, true)
        if (partialGrant) partialNotice = true
    }

    fun requestPerm() {
        if (Build.VERSION.SDK_INT >= 33) {
            permLauncher.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ))
        } else {
            @Suppress("DEPRECATION")
            permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    // 从系统设置授权返回时重查权限(onResume)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = hasReadPerm()
                if (granted) denied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // ── toast(先声明,供扫描/回收站等回调使用)──
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var toastActLabel by remember { mutableStateOf<String?>(null) }
    var toastAct by remember { mutableStateOf<(() -> Unit)?>(null) }
    var toastJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun toast(msg: String, actLabel: String?, act: (() -> Unit)?) {
        toastMsg = msg; toastActLabel = actLabel; toastAct = act
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(if (actLabel != null) 3600L else 1900L)
            toastMsg = null; toastActLabel = null; toastAct = null
        }
    }
    val toastFn: ToastFn = { m, l, a -> toast(m, l, a) }

    // ── 扫描 + 启动逻辑(授权后一次)──
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        if (!granted || started) return@LaunchedEffect
        started = true
        lib.scan()
        val pool = lib.pool()
        when (lib.startup) {
            "random" -> lib.setIdx(if (pool.isEmpty()) 0 else Random.nextInt(pool.size))
            "newest" -> lib.setIdx(0)
            "fixed" -> {
                val fb = lib.fixedBucket()
                if (fb.isNotEmpty() && pool.any { it.bucketId == fb }) {
                    lib.setScope("bucket:$fb")
                    val p2 = lib.pool()
                    lib.setIdx(if (p2.isEmpty()) 0 else Random.nextInt(p2.size))
                }
                // 固定夹不存在 → 回退全库最新
            }
            else -> { // last
                val last = prefs.getLong(SKeys.LAST_PHOTO, -1L)
                val at = pool.indexOfFirst { it.id == last }
                lib.setIdx(if (at >= 0) at else if (pool.isEmpty()) 0 else Random.nextInt(pool.size))
            }
        }
        if (lib.autoplay) lib.startPlay(auto = true)
        if (partialNotice) {
            toast("当前只授权了部分照片 · 可在系统设置放开全部", "去设置") {
                activity.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.packageName, null)
                    )
                )
            }
        }
    }

    // ── 遮罩层 ──
    var overlay by remember { mutableStateOf(Overlay.None) }
    var gridBucket by remember { mutableStateOf("") }

    // ── 回收站 ──
    var pendingTrash by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val ids = pendingTrash
        pendingTrash = emptySet()
        if (res.resultCode == Activity.RESULT_OK && ids.isNotEmpty()) {
            lib.removeIds(ids)
            toast("🗑 已移入回收站 · 可在系统相册恢复", null, null)
        }
    }
    fun trash(ids: Set<Long>) {
        if (ids.isEmpty()) return
        try {
            val uris = ids.mapNotNull { id -> lib.photos.firstOrNull { it.id == id }?.uri }
            if (uris.isEmpty()) return
            if (Build.VERSION.SDK_INT < 30) { toast("此系统不支持回收站删除", null, null); return }
            pendingTrash = ids
            val pir = MediaStore.createTrashRequest(activity.contentResolver, uris, true)
            trashLauncher.launch(IntentSenderRequest.Builder(pir.intentSender).build())
        } catch (e: Exception) {
            toast("删除失败:${e.message ?: "未知错误"}", null, null)
        }
    }

    // ── 返回键:遮罩 → 幻灯片 ──
    BackHandler(enabled = lib.playing && overlay == Overlay.None) { lib.stopPlay() }
    BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }

    // ── 根画面 ──
    Box(Modifier.fillMaxSize().background(FilmBlack)) {
        if (!granted) {
            PermScreen(
                denied = askedOnce && denied,
                onGrant = {
                    // 曾拒绝过 → 权限弹窗可能不再出现,改跳系统应用设置
                    if (askedOnce && denied) {
                        activity.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", activity.packageName, null)
                            )
                        )
                    } else requestPerm()
                },
                onSkip = {
                    askedOnce = true
                    prefs.putBool(SKeys.PERM_ASKED, true)
                    denied = true
                }
            )
        } else {
            // 主看图器
            ViewerScreen(
                lib = lib,
                toast = toastFn,
                onDrawer = { overlay = Overlay.Drawer },
                onGrid = { b -> gridBucket = b; overlay = Overlay.Grid },
                onSettings = { overlay = Overlay.Settings },
                onExif = { overlay = Overlay.Exif },
                onTrash = { lib.current()?.let { trash(setOf(it.id)) } },
            )

            // 抽屉(左滑面板 + 右侧暗区)
            AnimatedVisibility(
                visible = overlay == Overlay.Drawer,
                enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(340)) +
                    fadeIn(animationSpec = tween(240)),
                exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(280)) +
                    fadeOut(animationSpec = tween(220)),
            ) {
                Row(Modifier.fillMaxSize()) {
                    DrawerPanel(
                        lib = lib,
                        toast = toastFn,
                        onClose = { overlay = Overlay.None },
                        onOpenGrid = { b -> gridBucket = b; overlay = Overlay.Grid },
                        onOpenSettings = { overlay = Overlay.Settings },
                    )
                    Box(
                        Modifier.weight(1f).fillMaxSize().background(Color(0x66000000))
                            .clickableNoRipple { overlay = Overlay.None }
                    )
                }
            }

            // 印样网格
            if (overlay == Overlay.Grid) {
                Box(Modifier.fillMaxSize().background(FilmBlack)) {
                    ContactSheet(
                        lib = lib,
                        bucketId = gridBucket,
                        toast = toastFn,
                        onOpenPhoto = { p ->
                            lib.setScope("bucket:$gridBucket")
                            lib.jumpTo(p)
                            overlay = Overlay.None
                        },
                        onTrash = { ids -> trash(ids) },
                        onClose = { overlay = Overlay.None },
                    )
                }
            }

            // 设置面板
            if (overlay == Overlay.Settings) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x66000000))
                        .clickableNoRipple { overlay = Overlay.None }
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SettingsSheet(
                        lib = lib,
                        toast = toastFn,
                        onReplayPerm = { overlay = Overlay.Perm },
                        onClose = { overlay = Overlay.None }
                    )
                }
            }

            // EXIF 面板
            if (overlay == Overlay.Exif) {
                val cur = lib.current()
                Box(
                    Modifier.fillMaxSize().background(Color(0x66000000))
                        .clickableNoRipple { overlay = Overlay.None }
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    if (cur != null) {
                        ExifSheet(
                            photo = cur,
                            folderName = cur.bucketName,
                            isFav = cur.id in lib.favs,
                            onClose = { overlay = Overlay.None }
                        )
                    }
                }
            }

            // 重看首启权限屏(关于入口)
            if (overlay == Overlay.Perm) {
                Box(Modifier.fillMaxSize().background(FilmBlack)) {
                    PermScreen(
                        denied = false,
                        onGrant = { overlay = Overlay.None; requestPerm() },
                        onSkip = { overlay = Overlay.None }
                    )
                }
            }
        }

        // ── toast ──
        toastMsg?.let { m ->
            ToastBar(
                msg = m,
                actLabel = toastActLabel,
                onAct = { toastAct?.invoke(); toastMsg = null; toastActLabel = null }
            )
        }
    }
}

/** 顶部下拉的 toast:红左边条 + 可选动作(撤销等),与设计稿一致 */
@Composable
private fun ToastBar(msg: String, actLabel: String?, onAct: () -> Unit) {
    val y = remember { Animatable(-80f) }
    LaunchedEffect(msg) { y.animateTo(0f, tween(180)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.padding(top = 96.dp)
                .offset { IntOffset(0, y.value.toInt()) }
                .background(Safelight, RoundedCornerShape(10.dp)),   // 红底,左侧露出 3dp 作红条
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.padding(start = 3.dp)
                    .background(ToastBg, RoundedCornerShape(10.dp))
                    .clickableNoRipple(enabled = actLabel != null) { onAct() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(msg, color = Paper, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                if (actLabel != null) {
                    Spacer(Modifier.width(14.dp))
                    Text(actLabel, color = Safelight, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (!enabled) this else this.then(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
