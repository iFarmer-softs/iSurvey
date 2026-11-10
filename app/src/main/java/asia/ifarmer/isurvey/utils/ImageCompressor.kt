package asia.ifarmer.isurvey.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageCompressor {

    suspend fun compressImage(
        context: Context,
        inputFile: File,
        maxDimension: Int = 1200,
        quality: Int = 80
    ): File = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(inputFile.absolutePath, options)

        var width = options.outWidth
        var height = options.outHeight

        var inSampleSize = 1
        while (width / 2 >= maxDimension || height / 2 >= maxDimension) {
            width /= 2
            height /= 2
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val sampledBitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val isWebpSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val extension = if (isWebpSupported) "webp" else "jpg"
        val outputFile = File(context.cacheDir, "IMG_${timeStamp}_${System.currentTimeMillis()}.$extension")

        FileOutputStream(outputFile).use { out ->
            val format = if (isWebpSupported) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            sampledBitmap.compress(format, quality, out)
        }

        sampledBitmap.recycle()
        outputFile
    }
}

