package com.telegramyou.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.telegramyou.app.R

// Expressive type scale — rounded geometric feel via system default + weight contrast.
val TelegramYouTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

/**
 * Google Sans Flex, for the app's name and nothing else yet.
 *
 * From Google Fonts (`ofl/googlesansflex` in google/fonts), under the SIL
 * Open Font License, whose text ships beside it in
 * `assets/licenses/google_sans_flex_OFL.txt` as the licence asks. The
 * upstream file is a 4 MB variable font with six axes and a dozen scripts;
 * this copy is Latin only, with grade and slant at their defaults and
 * width pinned at 115 — a step wider than normal, which is the width the
 * title is set at — which brings it to 350 KB. Weight, optical size and
 * roundness stay variable. It was cut with fontTools:
 *
 *     pyftsubset "GoogleSansFlex[GRAD,ROND,opsz,slnt,wdth,wght].ttf" \
 *       --unicodes="U+0020-007E,U+00A0-00FF,U+2018-201F,U+2026" \
 *       --layout-features='*' --output-file=latin.ttf
 *     fonttools varLib.instancer latin.ttf GRAD=0 slnt=0 wdth=115 \
 *       -o google_sans_flex.ttf
 *
 * Kept variable, width would have been 840 KB for a word. To try another
 * width, cut the file again rather than widening it in code: the axis is
 * gone from this copy, and a width setting against it does nothing.
 *
 * Set the expressive way: rounded (ROND 100, the terminals Pixel's own clock
 * and Material 3 Expressive's headlines use), semi-bold and wide. It was
 * Medium, square-ended and normal width before — Google Sans Flex, but its
 * plainest cut, which read as a neutral label rather than as a name.
 *
 * Latin only is deliberate while the name is all it sets: there is no
 * Cyrillic in the family at all, so it could not carry a chat list anyway.
 */
/** The size the app's name is set at, which its optical size follows. */
val AppTitleSize = 26.sp

/** Between semi-bold and bold: heavy enough to be the name, short of shouting. */
val AppTitleWeight = FontWeight(650)

@OptIn(ExperimentalTextApi::class)
val AppTitleFontFamily = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = AppTitleWeight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(AppTitleWeight.weight),
            // Fully rounded ends; see above.
            FontVariation.Setting("ROND", 100f),
            // Set for the size the title is drawn at, so the letterforms are
            // the ones drawn for display rather than for body text.
            FontVariation.opticalSizing(AppTitleSize)
        )
    )
)
