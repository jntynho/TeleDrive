package com.infinity.drive.presentation.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.*

/** A bounded native tile. Non-normal EXIF orientation stays on Coil's orientation-aware path. */
internal data class ZoomRegion(val bitmap:Bitmap,val source:Rect,val original:IntSize)
internal fun decodeZoomRegion(path:String,viewport:IntSize,scale:Float,pan:Offset,pixelBudget:Long=2_000_000):ZoomRegion? {
    if(scale<1.5f||viewport.width<=0||viewport.height<=0||!File(path).isFile)return null
    return runCatching {
        val orientation=runCatching{ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        if(orientation!=ExifInterface.ORIENTATION_NORMAL)return@runCatching null
        @Suppress("DEPRECATION") val decoder=BitmapRegionDecoder.newInstance(path,false)
        try {
            val size=IntSize(decoder.width,decoder.height)
            val factor=min(viewport.width.toFloat()/size.width,viewport.height.toFloat()/size.height)*scale
            val x=viewport.width/2f-size.width*factor/2+pan.x
            val y=viewport.height/2f-size.height*factor/2+pan.y
            val region=Rect(floor(-x/factor).toInt().coerceIn(0,size.width-1),floor(-y/factor).toInt().coerceIn(0,size.height-1),
                ceil((viewport.width-x)/factor).toInt().coerceIn(1,size.width),ceil((viewport.height-y)/factor).toInt().coerceIn(1,size.height))
            if(region.isEmpty)return@runCatching null
            var sample=1
            while(sample*2<=1/factor && sample<64)sample*=2
            while(region.width().toLong()*region.height()/(sample.toLong()*sample)>pixelBudget)sample*=2
            val bitmap=decoder.decodeRegion(region,BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888})?:return@runCatching null
            ZoomRegion(bitmap,region,size)
        }finally{decoder.recycle()}
    }.getOrNull()
}
