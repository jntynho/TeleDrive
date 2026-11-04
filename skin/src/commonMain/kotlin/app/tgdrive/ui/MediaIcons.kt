package app.tgdrive.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Original compact player utility glyphs; transport glyphs are shared with the audio player. */
object MediaIcons {
    private fun line(name:String,path:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),
            strokeLineWidth=1.5f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
    }.build()
    val Fit by lazy { line("Player fit","M8 4H5C4.45 4 4 4.45 4 5V8M16 4H19C19.55 4 20 4.45 20 5V8M20 16V19C20 19.55 19.55 20 19 20H16M8 20H5C4.45 20 4 19.55 4 19V16") }
    val Crop by lazy { line("Player crop","M8 3V14C8 15.1 8.9 16 10 16H21M3 8H14C15.1 8 16 8.9 16 10V21") }
    val Rotate by lazy { line("Player rotate","M8 5H16C17.1 5 18 5.9 18 7V17C18 18.1 17.1 19 16 19H8C6.9 19 6 18.1 6 17V7C6 5.9 6.9 5 8 5ZM10 16H14M3 11C2 7 4 3.5 7 2M3 7V11H7M21 13C22 17 20 20.5 17 22M21 17V13H17") }
}
