package com.vpr.screenlate.languages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.overlay.ui.FlagCodes
import com.vpr.screenlate.overlay.ui.Flags
import com.vpr.screenlate.ui.theme.LocalEInk
import kotlin.math.roundToInt

/**
 * Which flags carry their language's code, by the user's flag choices ([FlagCodes]); the default choices where the
 * main screen does not provide them.
 */
val LocalFlagCodes = compositionLocalOf { FlagCodes() }

/**
 * [language]'s round flag of [country] ([Flags]), or its code on e-ink. A ring with a gap marks the [selected] one. A
 * flag that another language shows too carries the code on a badge over its lower edge ([LocalFlagCodes]). With
 * [onClick] it is a button that TalkBack reads as the language's name; otherwise it is decoration next to a name.
 */
@Composable
fun LanguageFlag(
    language: Language,
    country: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val name = language.displayName()
    val chosen = selected
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val flag = Flags.drawable(country)
    val eInk = LocalEInk.current
    // Outside the clipped circle, so the badge reaches the ring's outer edge as in the bubble menu.
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size + RING * 2 + GAP * 2)
                .clip(CircleShape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .border(RING, ring, CircleShape)
                .padding(RING + GAP)
                .then(
                    if (onClick != null) {
                        Modifier.semantics {
                            contentDescription = name
                            this.selected = chosen
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (eInk || flag == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(size)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                ) {
                    Text(Flags.code(language), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Image(painterResource(flag), contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
            }
        }
        if (!eInk && flag != null && LocalFlagCodes.current.coded(language, country)) {
            FlagCodeBadge(Flags.code(language), size, hang = 0f, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * [language]'s small round flag of [country] next to its name in a chip, list or title, or its code on e-ink or without
 * a flag. A flag that another language shows too carries the code on a badge over its lower edge ([LocalFlagCodes]).
 * Decoration: TalkBack reads the name beside it.
 */
@Composable
fun ChipFlag(language: Language, country: String, sizeDp: Int = 18, modifier: Modifier = Modifier) {
    val flag = Flags.drawable(country)
    if (LocalEInk.current || flag == null) {
        Text(Flags.code(language), style = MaterialTheme.typography.labelSmall, modifier = modifier.clearAndSetSemantics {})
    } else {
        Box(modifier.size(sizeDp.dp)) {
            Image(painterResource(flag), contentDescription = null, modifier = Modifier.matchParentSize().clip(CircleShape))
            if (LocalFlagCodes.current.coded(language, country)) {
                FlagCodeBadge(Flags.code(language), sizeDp.dp, hang = CHIP_BADGE_HANG, modifier = Modifier.matchParentSize())
            }
        }
    }
}

/**
 * The bubble menu's code badge, drawn at the bottom of [modifier]'s box: [code] in the text color on a rounded badge
 * in the background color, sized after the [flagSize] (the menu's look on a 40 dp flag, smaller below it, never under
 * 8 sp). [hang] is the part of the badge's height below the box. Wider than a small flag, it spills over its sides
 * without moving what is next to the flag.
 */
@Composable
private fun FlagCodeBadge(code: String, flagSize: Dp, hang: Float, modifier: Modifier = Modifier) {
    val scale = (flagSize / BADGE_FLAG_SIZE).coerceIn(BADGE_MIN_TEXT_SP / BADGE_TEXT_SP, 1f)
    Box(contentAlignment = Alignment.BottomCenter, modifier = modifier) {
        Text(
            code,
            style = TextStyle(fontSize = (BADGE_TEXT_SP * scale).sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clearAndSetSemantics {}
                .layout { measurable, constraints ->
                    val badge = measurable.measure(constraints)
                    layout(badge.width, badge.height) { badge.place(0, (badge.height * hang).roundToInt()) }
                }
                .wrapContentSize(Alignment.BottomCenter, unbounded = true)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(BADGE_CORNER * scale))
                .padding(horizontal = BADGE_PADDING * scale, vertical = BADGE_VERTICAL_PADDING * scale),
        )
    }
}

private val RING = 2.dp
private val GAP = 2.dp

/** The bubble menu's badge (`BubbleMenu.codeBadge`) on its 40 dp flag. */
private val BADGE_FLAG_SIZE = 40.dp
private const val BADGE_TEXT_SP = 10f
private const val BADGE_MIN_TEXT_SP = 8f
private val BADGE_PADDING = 4.dp
private val BADGE_VERTICAL_PADDING = 1.dp
private val BADGE_CORNER = 6.dp

/** The part of a chip flag's badge below the flag: about the 4 dp the menu's badge reaches below its flag. */
private const val CHIP_BADGE_HANG = 0.3f
