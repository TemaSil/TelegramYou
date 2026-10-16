package com.telegramyou.app.telegram.accounts

import android.content.Context
import android.util.Log
import com.telegramyou.app.telegram.TelegramClient
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The accounts on this phone and the one in use (2.3).
 *
 * Each account is a client of its own — for the live backend a TDLib
 * instance with its own database ([databaseDirName]) — made the first time
 * the account is used and kept running after another is switched to, so
 * switching back is instant and nothing it holds is lost. The one in use is
 * [active]; everything that talks to Telegram reads it through
 * TelegramYouApp.telegramRepository, and what holds on to it — the screens,
 * the notification service, the widgets — follows [active] as it changes.
 *
 * Who is signed in to each is noted here as its client says ([Accounts]),
 * so the switcher can show every account without opening the others.
 */
class AccountManager(
    context: Context,
    /** A client for one account, not yet started. */
    private val makeClient: (accountId: Int) -> TelegramClient,
    /** Called once for each repository as it is made and started. */
    private val onCreated: (TelegramRepository) -> Unit = {},
    /**
     * Where the list is kept. The demo entered from the live client's login
     * screen keeps its own, so accounts added inside it never reach the
     * live list — they would be empty TDLib databases there.
     */
    prefsName: String = PREFS
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _accounts = MutableStateFlow(decodeAccounts(prefs.getString(KEY_ACCOUNTS, null)))
    val accounts: StateFlow<Accounts> = _accounts.asStateFlow()

    private val repositories = HashMap<Int, TelegramRepository>()

    private val _active = MutableStateFlow(repositoryFor(_accounts.value.activeId))

    /** The account in use's repository, replaced as accounts are switched. */
    val active: StateFlow<TelegramRepository> = _active.asStateFlow()

    /** The account in use's repository, now. */
    val current: TelegramRepository get() = _active.value

    /** A repository already made for [id], without making one. */
    fun repositoryIfOpen(id: Int): TelegramRepository? = repositories[id]

    /** Into the account [id], which is opened if it is not yet. */
    fun switchTo(id: Int) {
        val accounts = _accounts.value
        if (accounts.slots.none { it.id == id } || id == accounts.activeId) return
        save(accounts.switchedTo(id))
        _active.value = repositoryFor(id)
    }

    /** A new, empty account, made the one in use: its sign-in screen. */
    fun add() {
        val accounts = _accounts.value
        if (!accounts.canAdd) return
        val added = accounts.added()
        save(added)
        _active.value = repositoryFor(added.activeId)
    }

    /** The account being added, given up: back to the one it was added from. */
    fun cancelAdding() {
        val accounts = _accounts.value
        if (!accounts.canCancelAdding || accounts.active.isSignedIn) return
        forget(accounts.activeId)
    }

    /**
     * An account off this phone: its client closed and its files deleted.
     * The account in use moves to another, or — when it was the last — to
     * an empty one, signed out.
     */
    private fun forget(id: Int, logOutFirst: Boolean = false) {
        val before = _accounts.value
        val after = before.removed(id)
        save(after)
        val repository = repositories.remove(id)
        if (after.activeId != before.activeId || repository === _active.value) {
            _active.value = repositoryFor(after.activeId)
        }
        repository?.let { closed ->
            scope.launch(Dispatchers.IO) {
                // TDLib deletes what it kept itself; what is left of the
                // directories — a client that never signed in, or one TDLib
                // had already emptied on logging out — goes after it.
                // Signed in and still wanted off the server too — the same
                // person a second time — so its session does not linger in
                // the account's devices.
                if (logOutFirst) runCatching { closed.logout() }
                runCatching { closed.destroy() }
                if (after.slots.none { it.id == id }) {
                    kotlinx.coroutines.delay(FILES_GRACE_MS)
                    deleteFiles(id)
                }
            }
        }
    }

    private fun repositoryFor(id: Int): TelegramRepository = repositories.getOrPut(id) {
        TelegramRepository(makeClient(id)).also { repository ->
            repository.start()
            onCreated(repository)
            watch(id, repository)
        }
    }

    /**
     * Who is signed in to [id], noted as the client says; an account signed
     * out of — from its own settings, or ended from another device — leaves
     * the phone when there are others to go on with.
     */
    private fun watch(id: Int, repository: TelegramRepository) {
        scope.launch {
            var wasReady = false
            repository.observeAuth().collect { auth ->
                if (repositories[id] !== repository) return@collect
                when (auth.state) {
                    AuthState.Ready -> {
                        wasReady = true
                        val me = auth.me ?: return@collect
                        val twin = _accounts.value.duplicateOf(id, me.id)
                        if (twin != null) {
                            // The same person signed in twice: the one
                            // already here is kept, and gone back to.
                            Log.i(TAG, "account $id is account ${twin.id} again")
                            val adding = _accounts.value.activeId == id
                            forget(id, logOutFirst = true)
                            if (adding) switchTo(twin.id)
                            return@collect
                        }
                        save(
                            _accounts.value.withProfile(
                                id,
                                userId = me.id,
                                name = me.displayName,
                                phone = me.phoneNumber.orEmpty(),
                                photoPath = me.photoPath
                            )
                        )
                    }
                    AuthState.WaitPhoneNumber, AuthState.WaitQrScan -> {
                        val slot = _accounts.value.slots.firstOrNull { it.id == id } ?: return@collect
                        if (!wasReady && !slot.isSignedIn) return@collect
                        wasReady = false
                        if (_accounts.value.slots.size > 1) {
                            forget(id)
                        } else {
                            save(_accounts.value.withProfile(id, userId = 0, name = "", phone = "", photoPath = null))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun save(accounts: Accounts) {
        _accounts.value = accounts
        prefs.edit().putString(KEY_ACCOUNTS, accounts.encode()).apply()
    }

    private fun deleteFiles(id: Int) {
        // Never the first account's: those directories are the ones the app
        // has always had, and an empty slot 0 signs in to them again.
        if (id == 0) return
        listOf(databaseDirName(id), filesDirName(id)).forEach { name ->
            runCatching { File(appContext.filesDir, name).deleteRecursively() }
        }
    }

    companion object {
        private const val TAG = "AccountManager"
        const val PREFS = "accounts"
        /** The demo's own list; see prefsName. */
        const val DEMO_PREFS = "accounts-demo"
        private const val KEY_ACCOUNTS = "accounts"
        /** Time for TDLib to close a destroyed instance before its directories go. */
        private const val FILES_GRACE_MS = 5_000L
    }
}
