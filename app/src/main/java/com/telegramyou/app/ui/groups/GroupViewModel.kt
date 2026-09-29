package com.telegramyou.app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.ForumTopic
import com.telegramyou.app.telegram.model.GroupManagement
import com.telegramyou.app.telegram.model.GroupMember
import com.telegramyou.app.telegram.model.GroupPermission
import com.telegramyou.app.telegram.model.InviteLink
import com.telegramyou.app.telegram.model.LinkExpiry
import com.telegramyou.app.telegram.model.LinkLimit
import com.telegramyou.app.telegram.model.MemberAction
import com.telegramyou.app.telegram.model.sortedTopics
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Running one group: its members and their standing, what members may do,
 * its invite links, and a forum's topics — what the info screen's admin
 * rows and the three screens behind them show.
 */
data class GroupUiState(
    val title: String = "",
    val management: GroupManagement? = null,
    val links: List<InviteLink>? = null,
    val topics: List<ForumTopic>? = null,
    /** Asked about before it is done: removing somebody is not undone by a tap. */
    val confirmingRemoval: GroupMember? = null,
    /** Said once, in a snackbar: "Nadia Orlova is an admin now". */
    val notice: String? = null,
    val errorMessage: String? = null
)

class GroupViewModel(
    private val repository: TelegramRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: Long = requireNotNull(savedStateHandle[Route.Chat.ARG_CHAT_ID]) {
        "GroupViewModel needs a ${Route.Chat.ARG_CHAT_ID} argument"
    }

    private val _uiState = MutableStateFlow(
        GroupUiState(title = repository.chats.value.firstOrNull { it.id == chatId }?.title.orEmpty())
    )
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    /** The id of the account, which has no menu of its own in the list. */
    val selfId: Long get() = repository.authState.value.me?.id ?: 0L

    /** Runs [work], turning a refusal into the screen's error line. */
    private fun attempt(failure: String, work: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = failureText(failure, e.message)) }
            }
        }
    }

    fun loadManagement() = attempt("Could not load the group") {
        val management = repository.groupManagement(chatId)
        _uiState.update { it.copy(management = management) }
    }

    /** From a member's menu. Removing asks first; everything else is done at once. */
    fun onMemberAction(member: GroupMember, action: MemberAction) {
        if (action == MemberAction.Remove) {
            _uiState.update { it.copy(confirmingRemoval = member) }
            return
        }
        apply(member, action)
    }

    fun onRemoveConfirmed() {
        val member = _uiState.value.confirmingRemoval ?: return
        _uiState.update { it.copy(confirmingRemoval = null) }
        apply(member, MemberAction.Remove)
    }

    fun onRemoveDismissed() = _uiState.update { it.copy(confirmingRemoval = null) }

    private fun apply(member: GroupMember, action: MemberAction) = attempt("Could not do that") {
        repository.applyMemberAction(chatId, member.user.id, action)
        val name = member.user.displayName
        val notice = when (action) {
            MemberAction.MakeAdmin -> "$name is an admin now"
            MemberAction.RemoveAdmin -> "$name is no longer an admin"
            MemberAction.Restrict -> "$name can't write here now"
            MemberAction.Unrestrict -> "$name can write again"
            MemberAction.Remove -> "$name was removed"
        }
        // Read back rather than guessed: the server may have given the new
        // admin rights of its own, or refused part of it.
        val management = repository.groupManagement(chatId)
        _uiState.update { it.copy(management = management ?: it.management, notice = notice) }
    }

    /**
     * One switch on the permissions screen. Shown at once and sent after;
     * if the server says no, the switches go back to what it has.
     */
    fun onPermissionChange(permission: GroupPermission, on: Boolean) {
        val current = _uiState.value.management ?: return
        val changed = permission.set(current.permissions, on)
        _uiState.update { it.copy(management = current.copy(permissions = changed)) }
        viewModelScope.launch {
            try {
                repository.setGroupPermissions(chatId, changed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        management = it.management?.copy(permissions = current.permissions),
                        errorMessage = failureText("Could not change that", e.message)
                    )
                }
            }
        }
    }

    fun loadLinks() = attempt("Could not load the links") {
        val links = repository.inviteLinks(chatId)
        _uiState.update { it.copy(links = links) }
    }

    fun onCreateLink(name: String, expiry: LinkExpiry, limit: LinkLimit) = attempt("Could not make the link") {
        repository.createInviteLink(chatId, name, expiry.from(System.currentTimeMillis() / 1000), limit.count)
        val links = repository.inviteLinks(chatId)
        _uiState.update { it.copy(links = links, notice = "Link created") }
    }

    fun onRevokeLink(link: InviteLink) = attempt("Could not revoke the link") {
        repository.revokeInviteLink(chatId, link.link)
        val links = repository.inviteLinks(chatId)
        _uiState.update { it.copy(links = links, notice = "Link revoked") }
    }

    fun loadTopics() = attempt("Could not load the topics") {
        val topics = sortedTopics(repository.forumTopics(chatId))
        _uiState.update { it.copy(topics = topics) }
    }

    fun onCreateTopic(name: String) = attempt("Could not start the topic") {
        repository.createForumTopic(chatId, name)
        val topics = sortedTopics(repository.forumTopics(chatId))
        _uiState.update { it.copy(topics = topics, notice = "Topic started") }
    }

    fun onNoticeShown() = _uiState.update { it.copy(notice = null) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }
}
