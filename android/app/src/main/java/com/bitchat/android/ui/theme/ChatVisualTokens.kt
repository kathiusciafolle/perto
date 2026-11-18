package com.bitchat.android.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Perto: fonte normal do sistema (sem serifa) no lugar da Geist Mono de "terminal".
 * A fonte do sistema já vem no celular, então continua funcionando sem internet.
 */
internal val BitchatFontFamily: FontFamily = FontFamily.Default

/** Exact typography, spacing, and opacity values exported for the chat transcript. */
internal object ChatVisualTokens {
    val MessageBodyFontSize: TextUnit = 15.sp
    val MessageBodyLineHeight: TextUnit = 21.sp
    val SenderFontSize: TextUnit = 13.sp
    val SenderLineHeight: TextUnit = 16.sp
    val SystemActionFontSize: TextUnit = 12.sp
    val SystemActionLineHeight: TextUnit = 16.sp
    val SystemTimeFontSize: TextUnit = 11.sp

    val MessageItemSpacing: Dp = 6.dp
    val SenderTopPadding: Dp = 8.dp
    val SenderToBodySpacing: Dp = 4.dp

    // MARK: - Bubble geometry (ChatUiMode.Bubbles)

    /** Rounded corner on the three "free" corners of a message bubble. */
    val BubbleCornerRadius: Dp = 18.dp

    /** Tightened corner on the speaker's own side, giving the bubble a subtle tail. */
    val BubbleTailRadius: Dp = 5.dp

    /** Padding inside a bubble, around the text. */
    val BubblePaddingHorizontal: Dp = 12.dp
    val BubblePaddingVertical: Dp = 8.dp

    /** A bubble never grows past this fraction of the list width, so long lines still wrap. */
    const val BubbleMaxWidthFraction: Float = 0.80f

    /**
     * Author-colour wash inside a bubble. Matches the mention-chip treatment so a tinted
     * bubble stays legible on both the dark and light chat surfaces.
     */
    const val BubbleBackgroundAlpha: Float = 0.22f

    /** Author-colour hairline around a bubble; stronger than the fill so the shape reads. */
    const val BubbleBorderAlpha: Float = 0.30f

    const val SenderSuffixAlpha: Float = 0.60f
    const val HighlightAlpha: Float = 0.20f
    const val MutedTextAlpha: Float = 0.50f

    val MessageBodyStyle = TextStyle(
        fontFamily = BitchatFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = MessageBodyFontSize,
        lineHeight = MessageBodyLineHeight,
    )

    val SenderStyle = TextStyle(
        fontFamily = BitchatFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = SenderFontSize,
        lineHeight = SenderLineHeight,
    )

    val SystemActionStyle = TextStyle(
        fontFamily = BitchatFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = SystemActionFontSize,
        lineHeight = SystemActionLineHeight,
    )
}
