package ke.zoetrope.data

import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 全库状态:MediaStore 索引、随机池、洗牌队列、收藏/隐藏/忽略、设置镜像。
 * 对应设计稿原型的 S 对象 + pool() + 幻灯片逻辑。
 */
class Library(private val context: Context, private val prefs: Prefs) {

    var photos by mutableStateOf<List<Photo>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set

    // ── 随机池治理 ──
    var favs by mutableStateOf(prefs.getStringSet(SKeys.FAVS).mapNotNull { it.toLongOrNull() }.toSet()); private set
    var hidden by mutableStateOf(prefs.getStringSet(SKeys.HIDDEN)); private set
    var ignored by mutableStateOf(prefs.getStringSet(SKeys.IGNORED).mapNotNull { it.toLongOrNull() }.toSet()); private set

    // ── 最近浏览的文件夹(MRU 在前,持久化)──
    var recentBuckets by mutableStateOf(
        prefs.getString(SKeys.RECENT_FOLDERS, "").split(',').filter { it.isNotEmpty() }
    ); private set

    fun recordBucketVisit(bucketId: String?) {
        if (bucketId == null || bucketId == "0") return
        recentBuckets = (listOf(bucketId) + recentBuckets.filter { it != bucketId }).take(20)
        prefs.putString(SKeys.RECENT_FOLDERS, recentBuckets.joinToString(","))
    }

    // ── 设置(镜像 prefs)──
    var startup by mutableStateOf(prefs.getString(SKeys.STARTUP, "last")); private set
    var autoplay by mutableStateOf(prefs.getBool(SKeys.AUTOPLAY, false)); private set
    var loop by mutableStateOf(prefs.getString(SKeys.LOOP, "reshuffle")); private set
    var transition by mutableStateOf(prefs.getString(SKeys.TRANSITION, "fade")); private set
    var caption by mutableStateOf(prefs.getBool(SKeys.CAPTION, false)); private set
    var edgebar by mutableStateOf(prefs.getBool(SKeys.EDGEBAR, true)); private set
    var theme by mutableStateOf(prefs.getString(SKeys.THEME, "system")); private set

    // ── 放映参数(跨启动记忆)──
    var scope by mutableStateOf(prefs.getString(SKeys.SCOPE, "all")); private set
    var speed by mutableStateOf(migrateSpeed(prefs.getFloat(SKeys.SPEED, 3f))); private set

    /** 旧档位(2/12/20 等)迁移到最近的三档(3/5/10)并回写 */
    private fun migrateSpeed(raw: Float): Float {
        val v = when { raw <= 4f -> 3f; raw <= 7f -> 5f; else -> 10f }
        if (v != raw) prefs.putFloat(SKeys.SPEED, v)
        return v
    }

    // ── 播放状态 ──
    var playing by mutableStateOf(false); private set
    var paused by mutableStateOf(false); private set
    var autoPaused by mutableStateOf(false); private set   // true = 放映中因交互自动暂停(非用户手动)
    var pendingAuto by mutableStateOf(false)
    var queue by mutableStateOf<List<Photo>>(emptyList()); private set
    var qp by mutableStateOf(0); private set
    var round by mutableStateOf(1); private set
    var idx by mutableStateOf(0); private set   // 非播放时在 pool() 中的位置

    // ── 设置写入(@JvmName 避免与 private set 生成的字节码 setter 撞名)──
    @JvmName("applyStartup") fun setStartup(v: String) { startup = v; prefs.putString(SKeys.STARTUP, v) }
    @JvmName("applyAutoplay") fun setAutoplay(v: Boolean) { autoplay = v; prefs.putBool(SKeys.AUTOPLAY, v) }
    @JvmName("applyLoop") fun setLoop(v: String) { loop = v; prefs.putString(SKeys.LOOP, v) }
    @JvmName("applyTransition") fun setTransition(v: String) { transition = v; prefs.putString(SKeys.TRANSITION, v) }
    @JvmName("applyCaption") fun setCaption(v: Boolean) { caption = v; prefs.putBool(SKeys.CAPTION, v) }
    @JvmName("applyEdgebar") fun setEdgebar(v: Boolean) { edgebar = v; prefs.putBool(SKeys.EDGEBAR, v) }
    @JvmName("applyTheme") fun setTheme(v: String) { theme = v; prefs.putString(SKeys.THEME, v) }
    @JvmName("applySpeed") fun setSpeed(v: Float) { speed = v; prefs.putFloat(SKeys.SPEED, v) }
    fun setFixedBucket(b: String) { prefs.putString(SKeys.FIXED_BUCKET, b) }
    fun fixedBucket() = prefs.getString(SKeys.FIXED_BUCKET, "")

    // ── 治理操作 ──
    fun toggleFav(id: Long) {
        favs = if (id in favs) favs - id else favs + id
        prefs.putStringSet(SKeys.FAVS, favs.map { it.toString() }.toSet())
    }
    fun setHidden(bucket: String, value: Boolean) {
        hidden = if (value) hidden + bucket else hidden - bucket
        prefs.putStringSet(SKeys.HIDDEN, hidden)
    }
    fun setIgnored(id: Long, value: Boolean) {
        ignored = if (value) ignored + id else ignored - id
        prefs.putStringSet(SKeys.IGNORED, ignored.map { it.toString() }.toSet())
    }

    // ── MediaStore 扫描(只查图片,视频天然排除)──
    suspend fun scan() = withContext(Dispatchers.IO) {
        val list = ArrayList<Photo>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val iBucketName = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val taken = if (c.isNull(iTaken)) c.getLong(iAdded) * 1000L else c.getLong(iTaken)
                    list.add(Photo(
                        id = id,
                        uri = ContentUrisHelper.imageUri(id),
                        name = c.getString(iName) ?: "",
                        path = c.getString(iPath) ?: "",
                        bucketId = c.getString(iBucket) ?: "0",
                        bucketName = c.getString(iBucketName) ?: "未命名",
                        dateTaken = taken,
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                    ))
                }
            }
        } catch (_: Exception) { }
        photos = list
        loaded = true
    }

    fun removeIds(ids: Set<Long>) {
        photos = photos.filter { it.id !in ids }
        favs = favs - ids
        ignored = ignored - ids
        prefs.putStringSet(SKeys.FAVS, favs.map { it.toString() }.toSet())
        prefs.putStringSet(SKeys.IGNORED, ignored.map { it.toString() }.toSet())
        if (playing) {
            if (pool().isEmpty()) stopPlay() else buildQueue(true)
        }
        // 非播放态删除后钳制 idx,防止越界(对应设计稿 btnDel 的 Math.min)
        else setIdx(minOf(idx, (pool().size - 1).coerceAtLeast(0)))
    }

    fun folders(): List<Folder> =
        photos.groupBy { it.bucketId }
            .map { (id, ps) -> Folder(id, ps.first().bucketName, ps.first(), ps.size) }
            .sortedByDescending { it.count }

    /** 随机池:全库 - 隐藏夹 - 忽略单张,再按范围过滤 */
    fun pool(): List<Photo> {
        var l = photos.filter { it.bucketId !in hidden && it.id !in ignored }
        when {
            scope == "fav" -> l = l.filter { it.id in favs }
            scope.startsWith("bucket:") -> {
                val b = scope.removePrefix("bucket:")
                l = l.filter { it.bucketId == b }
            }
        }
        return l
    }

    fun current(): Photo? = if (playing) queue.getOrNull(qp) else pool().getOrNull(idx)

    @JvmName("applyIdx")
    fun setIdx(i: Int) { idx = i.coerceIn(0, (pool().size - 1).coerceAtLeast(0)) }
    fun jumpTo(photo: Photo) {
        val at = pool().indexOfFirst { it.id == photo.id }
        idx = if (at >= 0) at else 0
    }
    fun pageManual(dir: Int) {
        val list = pool(); if (list.isEmpty()) return
        idx = (idx + dir + list.size) % list.size
    }

    fun scopeKey(): String = when {
        scope == "fav" -> "fav"
        scope == "all" -> "all"
        else -> "folder"
    }
    fun scopeName(): String = when (scope) {
        "fav" -> "收藏"
        "all" -> "全库"
        else -> photos.firstOrNull { it.bucketId == scope.removePrefix("bucket:") }?.bucketName ?: "文件夹"
    }

    @JvmName("applyScope")
    fun setScope(s: String) {
        scope = s
        prefs.putString(SKeys.SCOPE, s)
        idx = 0
        if (playing) {
            if (pool().isEmpty()) stopPlay() else buildQueue(true)
        }
    }

    /** Fisher-Yates 洗牌;keepCurrent = 切范围时保住当前那张 */
    fun buildQueue(keepCurrent: Boolean) {
        val curId = if (playing) queue.getOrNull(qp)?.id else pool().getOrNull(idx)?.id
        val list = pool().toMutableList()
        for (i in list.size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
        if (keepCurrent && curId != null) {
            val at = list.indexOfFirst { it.id == curId }
            if (at > 0) { val t = list[0]; list[0] = list[at]; list[at] = t }
        }
        queue = list; qp = 0; round = 1
    }

    /** @return false = 当前范围为空(调用方负责 toast) */
    fun startPlay(auto: Boolean = false): Boolean {
        if (pool().isEmpty()) return false
        pendingAuto = auto
        paused = false
        playing = true
        buildQueue(true)   // 幻灯片标准做法:停在当前张,走完一个间隔再随机切
        return true
    }
    /** 停止放映:把放映位置同步回手动指针,画面停在最后看到的那张 */
    fun stopPlay() {
        if (playing) {
            queue.getOrNull(qp)?.let { cur ->
                val at = pool().indexOfFirst { it.id == cur.id }
                if (at >= 0) idx = at
            }
        }
        playing = false; paused = false; autoPaused = false
    }

    fun togglePause() { if (playing) { paused = !paused; autoPaused = false } }

    /** 手动暂停:面板按钮/轻点图片走这里,不会自动恢复,需用户再点播放或轻点图片 */
    fun pauseManual() { if (playing && !paused) { paused = true; autoPaused = false } }

    /** 放映中因交互自动暂停(缩放/长按/开面板);与手动暂停区分 */
    fun pauseForInteraction() {
        if (playing && !paused) { paused = true; autoPaused = true }
    }
    /** 交互结束自动恢复;手动按 ❚❚ 暂停的不代恢复 */
    fun autoResume() {
        if (autoPaused && playing && paused) paused = false
        autoPaused = false
    }

    /** dir>0 下一张;一轮走完按「播完一轮」设置处理 */
    fun advance(dir: Int) {
        if (!playing) return
        if (dir > 0) {
            val p = qp + 1
            if (p >= queue.size) {
                // 池已被清空(删除/取消收藏)→ 停,不要静默冻结
                if (pool().isEmpty()) { stopPlay(); return }
                if (loop == "stop") { stopPlay(); return }
                round++
                buildQueue(false)
                return
            }
            qp = p
        } else {
            qp = (qp - 1).coerceAtLeast(0)
        }
    }

    fun saveLast(photo: Photo?) { if (photo != null) prefs.putLong(SKeys.LAST_PHOTO, photo.id) }
}

private object ContentUrisHelper {
    fun imageUri(id: Long) = android.content.ContentUris.withAppendedId(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
    )
}
