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
 * on the login screen, and the Settings title are set in [AppTitleStyle],
 * so this does not reach them.
 */
val TelegramYouTypography = Typography()

/**
 * Google Sans Flex, for the app's name and the Settings title.
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
 * Latin only, and not by choice: Google Sans Flex has no Cyrillic at all —
 * the upstream file has none to keep. A Russian title (2.6) fell back to the
 * system face, narrower and lighter, so it read as a different heading; it
 * is set in [AppTitleCyrillicFamily] instead. See [appTitleFamily].
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

/**
 * Google Sans — the face Flex was drawn from, which does have Cyrillic — for
 * a title in Russian. Same source and licence (`ofl/googlesans`, its text in
 * `assets/licenses/google_sans_OFL.txt`), cut to Cyrillic, digits and
 * punctuation, grade at its default, weight and optical size kept: 150 KB.
 *
 *     pyftsubset "GoogleSans[GRAD,opsz,wght].ttf" \
 *       --unicodes="U+0020-0040,U+00A0,U+00AB,U+00BB,U+0400-045F,U+0490-0491,U+2013-2014,U+2018-201F,U+2026" \
 *       --layout-features='*' --output-file=cyr.ttf
 *     fonttools varLib.instancer cyr.ttf GRAD=0 -o google_sans_cyrillic.ttf
 *
 * It has neither Flex's rounded ends nor its width, so a Russian title is
 * the same family and weight without those two; nothing nearer has the
 * letters.
 */
@OptIn(ExperimentalTextApi::class)
val AppTitleCyrillicFamily = FontFamily(
    Font(
        R.font.google_sans_cyrillic,
        weight = AppTitleWeight,
        variationSettings = FontVariation.Settings(FontVariation.weight(AppTitleWeight.weight))
    )
)

/** The title face for [text]: Google Sans Flex, or Google Sans where there is Cyrillic in it. */
fun appTitleFamily(text: String): FontFamily =
    if (text.any { it in '\u0400'..'\u04FF' }) AppTitleCyrillicFamily else AppTitleFontFamily

/**
 * A title with character: the app's name on Home, and the Settings title,
 * which the owner asked to match it. Everything but colour is set here, so
 * it reads the same whichever style it replaces.
 */
val AppTitleStyle = TextStyle(
    // Google Sans Flex, the face Google's own apps set their names in.
    fontFamily = AppTitleFontFamily,
    // A step down from 30, asked for once it was in Google Sans: this face
    // runs wider than the system one, and at 30 the name read louder than
    // the chats under it.
    fontSize = AppTitleSize,
    lineHeight = 32.sp,
    // Looser than the -1.2 the system face needed. This one's display cut
    // is already drawn tight, and pulling it in as far again ran the letters
    // together.
    letterSpacing = (-0.5).sp,
    // Not the scale's ExtraBold, which read as shouting; the face's own title
    // weight, which with its round ends and extra width is expressive rather
    // than loud.
    fontWeight = AppTitleWeight
)
