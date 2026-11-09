package nl.nextbuzz.bricktimer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.nextbuzz.bricktimer.logic.tr
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

object ImageUtils {
    private const val MAX_SIDE = 1280

    /** A fresh cache file for the camera to write into, shared through our FileProvider. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "build_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Removes camera photos once they are uploaded; the photo itself is never kept. */
    fun clearCameraCache(context: Context) {
        File(context.cacheDir, "photos").listFiles()?.forEach { it.delete() }
    }

    /** Downscales the photo to at most [MAX_SIDE] px, applies EXIF rotation and returns it as base64 JPEG. */
    suspend fun encodeForUpload(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException(tr("Kan de foto niet lezen.", "Couldn't read the photo."))

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException(tr("Kan de foto niet lezen.", "Couldn't read the photo."))

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val bitmap = if (scale < 1f || rotation != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
