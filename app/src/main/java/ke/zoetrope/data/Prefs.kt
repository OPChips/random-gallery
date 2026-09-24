package ke.zoetrope.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("zoetrope", Context.MODE_PRIVATE)

    fun getString(k: String, d: String) = sp.getString(k, d) ?: d
    fun putString(k: String, v: String) { sp.edit().putString(k, v).apply() }
    fun getBool(k: String, d: Boolean) = sp.getBoolean(k, d)
    fun putBool(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply() }
    fun getFloat(k: String, d: Float) = sp.getFloat(k, d)
    fun putFloat(k: String, v: Float) { sp.edit().putFloat(k, v).apply() }
    fun getLong(k: String, d: Long) = sp.getLong(k, d)
    fun putLong(k: String, v: Long) { sp.edit().putLong(k, v).apply() }
    fun getStringSet(k: String): Set<String> = sp.getStringSet(k, emptySet()) ?: emptySet()
    fun putStringSet(k: String, v: Set<String>) { sp.edit().putStringSet(k, v).apply() }
}

object SKeys {
    const val STARTUP = "startup"          // last | random | fixed | newest
    const val FIXED_BUCKET = "fixed_bucket"
    const val AUTOPLAY = "autoplay"
    const val LOOP = "loop"                // reshuffle | stop
    const val TRANSITION = "transition"    // fade | slide | none
    const val CAPTION = "caption"
    const val EDGEBAR = "edgebar"
    const val SPEED = "speed"
    const val SCOPE = "scope"
    const val LAST_PHOTO = "last_photo"
    const val FAVS = "favs"
    const val HIDDEN = "hidden"
    const val IGNORED = "ignored"
    const val PERM_ASKED = "perm_asked"
    const val THEME = "theme"              // system | dark
    const val RECENT_FOLDERS = "recent_folders"   // 最近浏览的文件夹 bucketId,逗号分隔,MRU 在前
}
