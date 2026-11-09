package com.infinity.drive.presentation.platform

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File

/** Bounded single-page fallback for phones without the Play Services document scanner. */
internal fun photoToScanPdf(photo:File,target:File):File {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(photo.absolutePath,bounds)
    require(bounds.outWidth>0&&bounds.outHeight>0){"The camera returned an unreadable image"}
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2
    val decoded=BitmapFactory.decodeFile(photo.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Couldn't read the scan")
    val orientation=runCatching{androidx.exifinterface.media.ExifInterface(photo.absolutePath).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
    val matrix=android.graphics.Matrix().apply{
        when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f)}
    }
    val bitmap=if(matrix.isIdentity)decoded else android.graphics.Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also{if(it!==decoded)decoded.recycle()}
    try{
        val pdf=PdfDocument()
        try{
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
            page.canvas.drawColor(Color.WHITE)
            val scale=minOf(555f/bitmap.width,802f/bitmap.height)
            val width=bitmap.width*scale;val height=bitmap.height*scale
            page.canvas.drawBitmap(bitmap,null,RectF((595-width)/2,(842-height)/2,(595+width)/2,(842+height)/2),Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            target.outputStream().use{pdf.writeTo(it)}
        }finally{pdf.close()}
        return target
    }catch(error:Exception){target.delete();throw error}
    finally{bitmap.recycle()}
}
