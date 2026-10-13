package com.vpr.screenlate.languages

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.overlay.ui.Flags
import com.vpr.screenlate.ui.theme.LocalEInk

/**
 * [language]'s round flag of [country] ([Flags]), or its code on e-ink. A ring with a gap marks the [selected] one.
 * With [onClick] it is a button that TalkBack reads as the language's name; otherwise it is decoration next to a name.
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
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
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
        val flag = Flags.drawable(country)
        if (LocalEInk.current || flag == null) {
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
}

private val RING = 2.dp
private val GAP = 2.dp
