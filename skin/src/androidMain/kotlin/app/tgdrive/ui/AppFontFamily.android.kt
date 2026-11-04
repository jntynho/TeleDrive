package app.tgdrive.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

actual val AppFontFamily: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(app.tgdrive.skin.R.font.roboto_variable, weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)
