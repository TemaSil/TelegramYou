package com.telegramyou.app.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.telegramyou.app.telegram.model.ButtonAction
import com.telegramyou.app.telegram.model.InlineButton
import com.telegramyou.app.telegram.model.PollContent
import com.telegramyou.app.telegram.model.ReplyKeyboard

/**
 * A poll inside its bubble.
 *
 * Before we vote: a radio button per option — or a checkbox and a Vote button
 * where several answers are allowed — because choosing one of a few is
 * exactly what those controls are for, and a row of them already says "pick
 * one" to anyone who has used Android. After: a bar per option with its
 * share, drawn with Material's own [LinearProgressIndicator] rather than a
 * filled rectangle.
 *
 * Every control is drawn in the colour of text on the bubble — onPrimary on
 * our own messages — so a poll we sent does not put primary on primary.
 */
@Composable
internal fun PollMessage(
    poll: PollContent,
    outgoing: Boolean,
    onVote: (Set<Int>) -> Unit
) {
    val onTint = if (outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val bubble = if (outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    // Picked but not yet sent, in a poll that allows several answers.
    var picked by remember(poll.id) { mutableStateOf(emptySet<Int>()) }
    Column(modifier = Modifier.widthIn(min = 240.dp)) {
        Text(
            poll.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = onTint
        )
        Text(
            poll.kindLabel,
            style = MaterialTheme.typography.labelMedium,
            color = onTint.copy(alpha = 0.7f)
        )
        Spacer(Modifier.padding(top = 6.dp))
        poll.options.forEachIndexed { index, option ->
            if (poll.showsResults) {
                PollResultRow(
                    text = option.text,
                    percentage = option.percentage,
                    isChosen = option.isChosen,
                    isCorrect = poll.isQuiz && index in poll.correctOptions,
                    isQuiz = poll.isQuiz,
                    onTint = onTint
                )
            } else if (poll.allowsMultiple) {
                val checked = index in picked
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .toggleable(
                            value = checked,
                            enabled = poll.acceptsVotes,
                            role = Role.Checkbox,
                            onValueChange = { picked = if (it) picked + index else picked - index }
                        )
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = onTint,
                            uncheckedColor = onTint.copy(alpha = 0.7f),
                            checkmarkColor = bubble
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(option.text, color = onTint)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        // One tap votes: a single-answer poll has nothing to
                        // confirm, and Telegram's own clients vote on the tap.
                        .selectable(
                            selected = false,
                            enabled = poll.acceptsVotes,
                            role = Role.RadioButton,
                            onClick = { onVote(setOf(index)) }
                        )
                ) {
                    RadioButton(
                        selected = false,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = onTint,
                            unselectedColor = onTint.copy(alpha = 0.7f)
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(option.text, color = onTint)
                }
            }
        }
        if (poll.isQuiz && poll.hasVoted && poll.explanation.isNotBlank()) {
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                poll.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = onTint.copy(alpha = 0.85f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                poll.votersLabel,
                style = MaterialTheme.typography.labelMedium,
                color = onTint.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
            when {
                poll.allowsMultiple && !poll.showsResults && poll.acceptsVotes -> Button(
                    onClick = {
                        onVote(picked)
                        picked = emptySet()
                    },
                    enabled = picked.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) { Text("Vote") }
                poll.canRetract -> TextButton(
                    onClick = { onVote(emptySet()) },
                    colors = ButtonDefaults.textButtonColors(contentColor = onTint)
                ) { Text("Retract vote") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PollResultRow(
    text: String,
    percentage: Int,
    isChosen: Boolean,
    isCorrect: Boolean,
    isQuiz: Boolean,
    onTint: Color
) {
    // Grows from where it was, so the vote landing is seen moving rather
    // than jumping — the theme's spring, like every other change here.
    val progress by animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pollShare"
    )
    val wrong = isQuiz && isChosen && !isCorrect
    val barColor = when {
        wrong -> MaterialTheme.colorScheme.error
        else -> onTint
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$percentage%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onTint,
                modifier = Modifier.width(44.dp)
            )
            Text(
                text,
                color = onTint,
                fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            val mark = when {
                isQuiz && isCorrect -> Icons.Rounded.CheckCircle
                wrong -> Icons.Rounded.Close
                isChosen -> Icons.Rounded.Check
                else -> null
            }
            if (mark != null) {
                Icon(
                    mark,
                    contentDescription = when {
                        isQuiz && isCorrect -> "Right answer"
                        wrong -> "Wrong answer"
                        else -> "Your vote"
                    },
                    tint = if (wrong) MaterialTheme.colorScheme.error else onTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.padding(top = 4.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = barColor,
            trackColor = onTint.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth().padding(start = 44.dp)
        )
    }
}

/**
 * A bot's inline buttons, under the bubble they belong to.
 *
 * Laid out against the bubble rather than on their own: the block is as wide
 * as the bubble or as wide as the buttons need, whichever is more, so a
 * short message with long buttons does not squeeze them into a column of
 * ellipses, and a long message does not leave them bunched at one side.
 * [bubble] is measured first; only the buttons are asked for their natural
 * width, which is safe because they hold nothing but text and an icon.
 */
@Composable
internal fun WithInlineKeyboard(
    rows: List<List<InlineButton>>,
    outgoing: Boolean,
    onPress: (InlineButton) -> Unit,
    bubble: @Composable () -> Unit
) {
    if (rows.isEmpty()) {
        bubble()
        return
    }
    val keyboard: @Composable () -> Unit = { InlineKeyboard(rows, onPress) }
    Layout(contents = listOf(bubble, keyboard)) { (bubbleMeasurables, keyboardMeasurables), constraints ->
        val bubblePlaceable = bubbleMeasurables.first().measure(constraints.copy(minWidth = 0))
        val buttons = keyboardMeasurables.first()
        val natural = buttons.maxIntrinsicWidth(Constraints.Infinity)
        val width = maxOf(bubblePlaceable.width, natural)
            .coerceAtMost(constraints.maxWidth)
            .coerceAtLeast(constraints.minWidth)
        val keyboardPlaceable = buttons.measure(Constraints.fixedWidth(width))
        val gap = 4.dp.roundToPx()
        layout(width, bubblePlaceable.height + gap + keyboardPlaceable.height) {
            bubblePlaceable.place(if (outgoing) width - bubblePlaceable.width else 0, 0)
            keyboardPlaceable.place(0, bubblePlaceable.height + gap)
        }
    }
}

@Composable
private fun InlineKeyboard(rows: List<List<InlineButton>>, onPress: (InlineButton) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { button ->
                    FilledTonalButton(
                        onClick = { onPress(button) },
                        enabled = button.action != ButtonAction.Unsupported,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                    ) {
                        Text(button.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Said with an icon, as Telegram does: a button that
                        // leaves the app, or one that only copies, should not
                        // look like one that talks to the bot.
                        val hint = when (button.action) {
                            is ButtonAction.OpenUrl -> Icons.AutoMirrored.Rounded.OpenInNew
                            is ButtonAction.CopyText -> Icons.Rounded.ContentCopy
                            else -> null
                        }
                        if (hint != null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(hint, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The keyboard a bot put under the composer: its keys, row by row.
 *
 * Tonal buttons on a container of their own, above the composer rather than
 * in place of the system keyboard — a custom IME surface would be a second
 * keyboard to maintain, and this is the same set of keys either way. Tall
 * keyboards scroll inside a cap, so a bot with thirty keys cannot push the
 * conversation off the screen.
 */
@Composable
internal fun ReplyKeyboardPanel(keyboard: ReplyKeyboard, onKey: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                keyboard.rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = { onKey(key.text) },
                                enabled = key.sendsText,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Text(key.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
