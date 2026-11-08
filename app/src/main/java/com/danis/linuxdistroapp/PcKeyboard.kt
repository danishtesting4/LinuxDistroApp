package com.danis.linuxdistroapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val KEY_BACKSPACE = 0xff08
private const val KEY_TAB = 0xff09
private const val KEY_ENTER = 0xff0d
private const val KEY_ESCAPE = 0xff1b
private const val KEY_CAPS_LOCK = 0xffe5
private const val KEY_SHIFT = 0xffe1
private const val KEY_CONTROL = 0xffe3
private const val KEY_ALT = 0xffe9
private const val KEY_SUPER = 0xffeb

private data class PcKey(
    val label: String,
    val keySym: Int,
    val modifier: Int? = null,
    val width: Dp? = null,
    val description: String = label
)

@Composable
fun PcKeyboard(
    client: VncClient,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeModifiers by remember { mutableStateOf(emptySet<Int>()) }
    val colors = MaterialTheme.colorScheme
    val spacing = 4.dp

    fun press(key: PcKey) {
        val modifierKey = key.modifier
        if (modifierKey != null) {
            activeModifiers = if (modifierKey in activeModifiers) {
                activeModifiers - modifierKey
            } else {
                activeModifiers + modifierKey
            }
        } else {
            client.sendKeyCombination(key.keySym, activeModifiers.toList())
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        BoxWithConstraints {
            val compact = maxHeight < 420.dp
            val keyHeight = if (compact) 40.dp else 48.dp
            val maxPanelHeight = maxHeight * if (compact) 0.74f else 0.56f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanelHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PC keyboard",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Modifiers stay on until toggled",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onHide) {
                        Text("Hide")
                    }
                }

                PcKeyRow(
                    keys = functionKeys,
                    activeModifiers = activeModifiers,
                    keyHeight = keyHeight,
                    spacing = spacing,
                    onKey = ::press
                )
                PcKeyRow(
                    keys = numberKeys,
                    activeModifiers = activeModifiers,
                    keyHeight = keyHeight,
                    spacing = spacing,
                    onKey = ::press
                )
                PcKeyRow(
                    keys = topLetterKeys,
                    activeModifiers = activeModifiers,
                    keyHeight = keyHeight,
                    spacing = spacing,
                    onKey = ::press
                )
                PcKeyRow(
                    keys = homeLetterKeys,
                    activeModifiers = activeModifiers,
                    keyHeight = keyHeight,
                    spacing = spacing,
                    onKey = ::press
                )
                PcKeyRow(
                    keys = bottomLetterKeys,
                    activeModifiers = activeModifiers,
                    keyHeight = keyHeight,
                    spacing = spacing,
                    onKey = ::press
                )
                PcKeyRow(
                    keys = controlKeys,
                    activeModifiers = activeModifiers,
                    keyHeight = keyHeight,
                    spacing = spacing,
                    onKey = ::press
                )
            }
        }
    }
}

@Composable
private fun PcKeyRow(
    keys: List<PcKey>,
    activeModifiers: Set<Int>,
    keyHeight: Dp,
    spacing: Dp,
    onKey: (PcKey) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val keyWidth = ((maxWidth - spacing * (keys.size - 1)) / keys.size)
            .coerceIn(28.dp, 48.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            keys.forEach { key ->
                val isActive = key.modifier != null && key.modifier in activeModifiers
                val keyColors = MaterialTheme.colorScheme
                Surface(
                    onClick = { onKey(key) },
                    modifier = Modifier
                        .width(key.width ?: keyWidth)
                        .height(keyHeight)
                        .semantics { contentDescription = key.description },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) keyColors.primaryContainer else keyColors.surface,
                    contentColor = if (isActive) {
                        keyColors.onPrimaryContainer
                    } else {
                        keyColors.onSurface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isActive) keyColors.primary else keyColors.outlineVariant
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = key.label,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private val functionKeys = buildList {
    add(PcKey("Esc", KEY_ESCAPE, width = 42.dp))
    for (number in 1..12) {
        add(PcKey("F$number", 0xffbe + number - 1, width = 36.dp))
    }
    add(PcKey("Ins", 0xff63, width = 42.dp))
    add(PcKey("Home", 0xff50, width = 48.dp))
    add(PcKey("PgUp", 0xff55, width = 44.dp))
    add(PcKey("Del", 0xffff, width = 42.dp))
    add(PcKey("End", 0xff57, width = 42.dp))
    add(PcKey("PgDn", 0xff56, width = 44.dp))
}

private val numberKeys = listOf(
    PcKey("`", '`'.code),
    PcKey("1", '1'.code),
    PcKey("2", '2'.code),
    PcKey("3", '3'.code),
    PcKey("4", '4'.code),
    PcKey("5", '5'.code),
    PcKey("6", '6'.code),
    PcKey("7", '7'.code),
    PcKey("8", '8'.code),
    PcKey("9", '9'.code),
    PcKey("0", '0'.code),
    PcKey("-", '-'.code),
    PcKey("=", '='.code),
    PcKey("Bksp", KEY_BACKSPACE, width = 54.dp)
)

private val topLetterKeys = listOf(
    PcKey("Tab", KEY_TAB, width = 48.dp),
    *"qwertyuiop".map { PcKey(it.toString(), it.code) }.toTypedArray(),
    PcKey("[", '['.code),
    PcKey("]", ']'.code),
    PcKey("\\", '\\'.code)
)

private val homeLetterKeys = listOf(
    PcKey("Caps", KEY_CAPS_LOCK, width = 52.dp),
    *"asdfghjkl".map { PcKey(it.toString(), it.code) }.toTypedArray(),
    PcKey(";", ';'.code),
    PcKey("'", '\''.code),
    PcKey("Enter", KEY_ENTER, width = 54.dp)
)

private val bottomLetterKeys = listOf(
    PcKey("Shift", KEY_SHIFT, modifier = KEY_SHIFT, width = 56.dp),
    *"zxcvbnm".map { PcKey(it.toString(), it.code) }.toTypedArray(),
    PcKey(",", ','.code),
    PcKey(".", '.'.code),
    PcKey("/", '/'.code),
    PcKey("Shift", KEY_SHIFT, modifier = KEY_SHIFT, width = 56.dp)
)

private val controlKeys = listOf(
    PcKey("Ctrl", KEY_CONTROL, modifier = KEY_CONTROL, width = 50.dp),
    PcKey("Super", KEY_SUPER, modifier = KEY_SUPER, width = 50.dp),
    PcKey("Alt", KEY_ALT, modifier = KEY_ALT, width = 44.dp),
    PcKey("Space", ' '.code, width = 92.dp),
    PcKey("Menu", 0xff67, width = 48.dp),
    PcKey("Left", 0xff51, width = 44.dp, description = "Left arrow"),
    PcKey("Up", 0xff52, width = 40.dp, description = "Up arrow"),
    PcKey("Down", 0xff54, width = 44.dp, description = "Down arrow"),
    PcKey("Right", 0xff53, width = 48.dp, description = "Right arrow")
)
