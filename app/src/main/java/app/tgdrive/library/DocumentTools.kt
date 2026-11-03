package app.tgdrive.library

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import java.io.File

/** Compatibility image decoding and PDF creation; old OCR jobs are retired. */
class DocumentTools(private val repository: LibraryRepository) {
    suspend fun createPdf(images: List<Uri>, trim: Float, rotate: Int, grayscale: Boolean): File = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()&&images.size<=50)
        val output=File(repository.context.filesDir,"captures/${repository.session.profileId}/scan-${newId()}.pdf").apply { parentFile!!.mkdirs() }
        val document=PdfDocument()
        try {
            images.forEachIndexed { index,uri ->
                ensureActive()
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                repository.context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,options) }
                var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>2400)sample*=2
                val decoded=repository.context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) } ?: error("Selected image cannot be decoded.")
                val orientation=repository.context.contentResolver.openInputStream(uri)!!.use{runCatching{androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)}
                val source=orientBitmap(decoded,orientation)
                var bitmap=source
                try {
                    val t=trim.coerceIn(0f,0.2f)
                    val matrix=Matrix().apply { postRotate(rotate.toFloat()) }
                    bitmap=Bitmap.createBitmap(source,(source.width*t).toInt(),(source.height*t).toInt(),(source.width*(1-2*t)).toInt().coerceAtLeast(1),(source.height*(1-2*t)).toInt().coerceAtLeast(1),matrix,true)
                    val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create())
                    page.canvas.drawColor(Color.WHITE)
                    val ratio=minOf(555f/bitmap.width,802f/bitmap.height)
                    val width=bitmap.width*ratio;val height=bitmap.height*ratio
                    val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    if(grayscale)paint.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    page.canvas.drawBitmap(bitmap,null,RectF((595-width)/2,(842-height)/2,(595+width)/2,(842+height)/2),paint)
                    document.finishPage(page)
                }finally { if(bitmap!==source)bitmap.recycle();source.recycle() }
            }
            output.outputStream().use { document.writeTo(it) }
            output
        }catch(e:Exception){output.delete();throw e}finally{document.close()}
    }
    fun decode(file: File, max: Int = 1800): Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.absolutePath,bounds)
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>max)sample*=2
        val bitmap=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("Image format is not supported.")
        val orientation=runCatching{androidx.exifinterface.media.ExifInterface(file).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
        return orientBitmap(bitmap,orientation)
    }
}
