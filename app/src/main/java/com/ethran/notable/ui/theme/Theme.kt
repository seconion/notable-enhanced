package com.ethran.notable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.datastore.GlobalAppSettings

private fun getDarkColorPalette(accentColor: AppSettings.AccentColor) = darkColors(
    primary = getAccentColor(accentColor),
    primaryVariant = getAccentColorVariant(accentColor),
    secondary = Teal200
)

private fun getLightColorPalette(accentColor: AppSettings.AccentColor) = lightColors(
    primary = getAccentColor(accentColor),
    primaryVariant = getAccentColorVariant(accentColor),
    secondary = Teal200

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

private fun getAccentColor(accentColor: AppSettings.AccentColor): Color {
    return when (accentColor) {
        AppSettings.AccentColor.Black -> Black
        AppSettings.AccentColor.Blue -> Blue
        AppSettings.AccentColor.Red -> Red
        AppSettings.AccentColor.Green -> Green
        AppSettings.AccentColor.Orange -> Orange
        AppSettings.AccentColor.Purple -> Purple
        AppSettings.AccentColor.Teal -> Teal
    }
}

private fun getAccentColorVariant(accentColor: AppSettings.AccentColor): Color {
    return when (accentColor) {
        AppSettings.AccentColor.Black -> BlackVariant
        AppSettings.AccentColor.Blue -> BlueVariant
        AppSettings.AccentColor.Red -> RedVariant
        AppSettings.AccentColor.Green -> GreenVariant
        AppSettings.AccentColor.Orange -> OrangeVariant
        AppSettings.AccentColor.Purple -> PurpleVariant
        AppSettings.AccentColor.Teal -> TealVariant
    }
}

@Composable
fun InkaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val accentColor = GlobalAppSettings.current.accentColor

    val colors = if (darkTheme) {
        getDarkColorPalette(accentColor)
    } else {
        getLightColorPalette(accentColor)
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}