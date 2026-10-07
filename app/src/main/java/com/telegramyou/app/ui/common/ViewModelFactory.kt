package com.telegramyou.app.ui.common

import com.telegramyou.app.ui.folders.FolderEditViewModel
import com.telegramyou.app.ui.folders.FoldersViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.settings.InMemoryQueryHistory
import com.telegramyou.app.settings.QueryHistory
import com.telegramyou.app.ui.auth.AuthViewModel
import com.telegramyou.app.ui.chat.ChatViewModel
import com.telegramyou.app.ui.chat.SharedMediaViewModel
import com.telegramyou.app.ui.music.MyMusicViewModel
import com.telegramyou.app.ui.groups.GroupViewModel
import com.telegramyou.app.ui.home.HomeViewModel
import com.telegramyou.app.ui.newchat.NewChatViewModel
import com.telegramyou.app.ui.people.BlockedViewModel
import com.telegramyou.app.ui.people.ContactsViewModel
import com.telegramyou.app.ui.people.PersonViewModel
import com.telegramyou.app.ui.proxy.ProxyViewModel
import com.telegramyou.app.ui.settings.DevicesViewModel
import com.telegramyou.app.ui.settings.StorageViewModel
import com.telegramyou.app.ui.settings.PrivacyViewModel
import com.telegramyou.app.ui.stories.StoryViewModel
import com.telegramyou.app.ui.stories.NewStoryViewModel

/**
 * Builds the screen state holders, all of which need the repository.
 *
 * The repository is created once in `TelegramYouApp` and owns the TDLib
 * connection, so it cannot be constructed by a ViewModel itself. This is the
 * one place that hands it over; screens ask for a ViewModel and never see
 * where it came from.
 *
 * A ViewModel that needs a navigation argument — a chat id, a user id —
 * takes it through `SavedStateHandle` rather than through this factory, so
 * the argument survives process death with the rest of the state.
 */
fun telegramViewModelFactory(
    repository: TelegramRepository,
    queryHistory: QueryHistory = InMemoryQueryHistory(),
    /** The app's voice messages; chats make their own where none is given. */
    voice: com.telegramyou.app.music.VoicePlayback? = null
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { AuthViewModel(repository) }
        initializer { HomeViewModel(repository, queryHistory) }
        initializer {
            if (voice != null) ChatViewModel(repository, createSavedStateHandle(), voice)
            else ChatViewModel(repository, createSavedStateHandle())
        }
        initializer { GroupViewModel(repository, createSavedStateHandle()) }
        initializer { SharedMediaViewModel(repository, createSavedStateHandle()) }
        initializer { MyMusicViewModel(repository) }
        initializer { com.telegramyou.app.ui.music.MusicLibraryViewModel(repository) }
        initializer { com.telegramyou.app.ui.downloads.DownloadsViewModel(repository) }
        initializer { StoryViewModel(repository, createSavedStateHandle()) }
        initializer { NewStoryViewModel(repository) }
        initializer { NewChatViewModel(repository) }
        initializer { ProxyViewModel(repository) }
        initializer { DevicesViewModel(repository) }
        initializer { StorageViewModel(repository) }
        initializer { com.telegramyou.app.ui.settings.NotificationsViewModel(repository) }
        initializer { PrivacyViewModel(repository) }
        initializer { PersonViewModel(repository, createSavedStateHandle()) }
        initializer { BlockedViewModel(repository) }
        initializer { ContactsViewModel(repository) }
        initializer { FoldersViewModel(repository) }
        initializer { FolderEditViewModel(repository, createSavedStateHandle()) }
    }
