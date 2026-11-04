package app.tgdrive.ui

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Library destinations, distinct from the ZIP document type and transfer actions. */
object LibraryIcons {
    private fun line(name:String,path:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply{
        addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.5f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
    }.build()
    val Activity by lazy{line("Library activity","M4 6H15M4 12H7M4 18H7M21 15C21 18.3 18.3 21 15 21C11.7 21 9 18.3 9 15C9 11.7 11.7 9 15 9C18.3 9 21 11.7 21 15ZM15 12V15L17 16")}
    val Archive by lazy{line("Archived library","M5 9V18C5 19.1 5.9 20 7 20H17C18.1 20 19 19.1 19 18V9M4 4H20V9H4ZM10 13H14")}
    val Hidden by lazy{line("Hidden library","M3 3L21 21M9.5 5.5C10.3 5.2 11.1 5 12 5C17 5 21 12 21 12C21 12 19.9 14 18.1 15.8M14.5 18.5C13.7 18.8 12.9 19 12 19C7 19 3 12 3 12C3 12 4.1 10 5.9 8.2M9.9 9.9C8.7 11.1 8.7 12.9 9.9 14.1C11.1 15.3 12.9 15.3 14.1 14.1")}
}
