package com.telegramyou.app.ui.stories

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.StoryAudience
import com.telegramyou.app.ui.chat.InlineVideo
import com.telegramyou.app.ui.chat.RecentPhotoCarousel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.telegramyou.app.ui.failureText
import com.telegramyou.app.ui.icons.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A story being made: the picture or video chosen, its caption, who it is
 * for, and whether it is on its way. [posted] tells the screen to go back.
 */
data class NewStoryUiState(
    val uri: String? = null,
    val isVideo: Boolean = false,
    val caption: String = "",
    val audience: StoryAudience = StoryAudience.Everyone,
    val isPosting: Boolean = false,
    val posted: Boolean = false,
    val errorMessage: String? = null
)

class NewStoryViewModel(private val repository: TelegramRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NewStoryUiState())
    val uiState: StateFlow<NewStoryUiState> = _uiState.asStateFlow()

    fun onPicked(uri: String, isVideo: Boolean) = _uiState.update { it.copy(uri = uri, isVideo = isVideo) }

    fun onCaptionChange(text: String) = _uiState.update { it.copy(caption = text) }

    fun onAudienceChange(audience: StoryAudience) = _uiState.update { it.copy(audience = audience) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    fun onPost() {
        val state = _uiState.value
        val uri = state.uri ?: return
        if (state.isPosting) return
        _uiState.update { it.copy(isPosting = true) }
        viewModelScope.launch {
            try {
                repository.postStory(uri, state.isVideo, state.caption.trim(), state.audience)
                _uiState.update { it.copy(isPosting = false, posted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isPosting = false, errorMessage = failureText("Could not post the story", e.message))
                }
            }
        }
    }
}

/**
 * Posting a story, from "My story" in the rail: a recent photo from the
 * strip, or anything from Android's photo picker; a caption; who sees it,
 * as Material's segmented buttons; then Post. A day later it is gone, as
 * every story is.
 *
 * No editor — no drawing, text or stickers on it. Those are a screen of
 * their own, and the story is the picture.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewStoryScreen(
    state: NewStoryUiState,
    onBack: () -> Unit,
    onPicked: (uri: String, isVideo: Boolean) -> Unit,
    onCaptionChange: (String) -> Unit,
    onAudienceChange: (StoryAudience) -> Unit,
    onPost: () -> Unit,
    onErrorShown: () -> Unit,
    onPosted: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            onPicked(uri.toString(), isVideo)
        }
    }
    LaunchedEffect(state.posted) { if (state.posted) onPosted() }
    state.errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New story") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Symbols.Close, contentDescription = "Close") }
                }
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val uri = state.uri
            if (uri == null) {
                Text(
                    "Share a photo or a video for a day.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecentPhotoCarousel(onPick = { picked -> onPicked(picked, false) })
            } else {
                StoryPreview(uri = uri, isVideo = state.isVideo)
            }
            FilledTonalButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Symbols.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (uri == null) "Choose a photo or video" else "Choose another")
            }
            if (uri != null) {
                OutlinedTextField(
                    value = state.caption,
                    onValueChange = onCaptionChange,
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Who can see it",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StoryAudience.entries.forEachIndexed { index, audience ->
                        SegmentedButton(
                            selected = state.audience == audience,
                            onClick = { onAudienceChange(audience) },
                            shape = SegmentedButtonDefaults.itemShape(index, StoryAudience.entries.size)
                        ) {
                            Text(audience.label, maxLines = 1)
                        }
                    }
                }
                Button(
                    onClick = onPost,
                    enabled = !state.isPosting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    if (state.isPosting) {
                        LoadingIndicator(Modifier.size(24.dp))
                    } else {
                        Text("Post story")
                    }
                }
            }
        }
    }
}

/** The chosen picture or video at a story's shape. */
@Composable
private fun StoryPreview(uri: String, isVideo: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .height(PREVIEW_HEIGHT)
                .aspectRatio(STORY_ASPECT)
        ) {
            if (isVideo) {
                // The video itself, silent and on a loop, as the story will
                // start — the chat's own inline player, cropped to the shape.
                InlineVideo(
                    path = uri,
                    playing = true,
                    muted = true,
                    loop = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .semantics { contentDescription = "Story video" }
                )
            } else {
                AsyncImage(
                    model = uri,
                    contentDescription = "Story picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                )
            }
        }
    }
}

/** A story's shape: a phone screen standing up. */
private const val STORY_ASPECT = 9f / 16f
private val PREVIEW_HEIGHT = 360.dp
