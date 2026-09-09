package ke.zoetrope.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val bucketId: String,
    val bucketName: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
)

data class Folder(
    val bucketId: String,
    val name: String,
    val cover: Photo?,
    val count: Int,
)
