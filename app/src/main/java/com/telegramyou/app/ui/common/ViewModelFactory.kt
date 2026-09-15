package com.telegramyou.app.ui.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.ui.home.HomeViewModel

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
fun telegramViewModelFactory(repository: TelegramRepository): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { HomeViewModel(repository) }
    }
