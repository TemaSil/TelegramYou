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

/**
 * Material 3's own type scale, untouched.
 *
 * This was a scale of its own — headlines and titles in Bold and SemiBold,
 * labels in Bold, sizes nudged off the spec — and it made every screen that
 * uses the scale as it comes read heavier than Android around it: Settings
 * above all, whose title, group headings and rows are nothing but the
 * scale. The owner asked for the standard one. The app's name, on Home and
 * on the login screen, is set in its own face and weight and size, so this
 * does not reach it.
 */
val TelegramYouTypography = Typography()

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
