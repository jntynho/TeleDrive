package app.tgdrive.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Original player utility glyphs. Heart uses the existing Solar path; attribution: third_party/solar. */
object MediaIcons {
    private fun line(name:String,path:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),
            strokeLineWidth=2f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
    }.build()
    private fun solid(name:String,path:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(),fill=SolidColor(Color.Black))
    }.build()
    val Play by lazy { solid("Player play","M7 3.8C5.7 3 4 3.9 4 5.4V18.6C4 20.1 5.7 21 7 20.2L20 13.6C21.3 12.9 21.3 11.1 20 10.4Z") }
    val Pause by lazy { solid("Player pause","M5 4H8C8.55 4 9 4.45 9 5V19C9 19.55 8.55 20 8 20H5C4.45 20 4 19.55 4 19V5C4 4.45 4.45 4 5 4ZM16 4H19C19.55 4 20 4.45 20 5V19C20 19.55 19.55 20 19 20H16C15.45 20 15 19.55 15 19V5C15 4.45 15.45 4 16 4Z") }
    val Previous by lazy { solid("Previous track","M4 4H6C6.55 4 7 4.45 7 5V19C7 19.55 6.55 20 6 20H4C3.45 20 3 19.55 3 19V5C3 4.45 3.45 4 4 4ZM19 4.2C20.3 3.4 22 4.3 22 5.8V18.2C22 19.7 20.3 20.6 19 19.8L8.5 13.6C7.2 12.9 7.2 11.1 8.5 10.4Z") }
    val Next by lazy { solid("Next track","M18 4H20C20.55 4 21 4.45 21 5V19C21 19.55 20.55 20 20 20H18C17.45 20 17 19.55 17 19V5C17 4.45 17.45 4 18 4ZM5 4.2C3.7 3.4 2 4.3 2 5.8V18.2C2 19.7 3.7 20.6 5 19.8L15.5 13.6C16.8 12.9 16.8 11.1 15.5 10.4Z") }
    val Fit by lazy { line("Player fit","M8 4H5C4.45 4 4 4.45 4 5V8M16 4H19C19.55 4 20 4.45 20 5V8M20 16V19C20 19.55 19.55 20 19 20H16M8 20H5C4.45 20 4 19.55 4 19V16") }
    val Crop by lazy { line("Player crop","M8 3V14C8 15.1 8.9 16 10 16H21M3 8H14C15.1 8 16 8.9 16 10V21") }
    val Rotate by lazy { line("Player rotate","M8 8H18C19.1 8 20 8.9 20 10V16C20 17.1 19.1 18 18 18H8C6.9 18 6 17.1 6 16V10C6 8.9 6.9 8 8 8ZM17 11V15M4 12V8C4 4.7 6.7 2 10 2H14M11 5L14 2L11 1") }
    val HeartFilled by lazy { ImageVector.Builder("Favorite filled",24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString("M2 9.1371C2 14 6.01943 16.5914 8.96173 18.9109C10 19.7294 11 20.5 12 20.5C13 20.5 14 19.7294 15.0383 18.9109C17.9806 16.5914 22 14 22 9.1371C22 4.27416 16.4998 0.825464 12 5.50063C7.50016 0.825464 2 4.27416 2 9.1371Z").toNodes(),fill=SolidColor(Color.Black))
    }.build() }
}
