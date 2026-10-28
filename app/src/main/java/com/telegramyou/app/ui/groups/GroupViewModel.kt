package com.telegramyou.app.ui.groups

import com.telegramyou.app.i18n.tr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramyou.app.navigation.Route
import com.telegramyou.app.telegram.TelegramRepository
import com.telegramyou.app.telegram.model.AdminRights
import com.telegramyou.app.telegram.model.ForumTopic
import com.telegramyou.app.telegram.model.JoinRequest
import com.telegramyou.app.telegram.model.TopicAction
import com.telegramyou.app.telegram.model.GroupManagement
import com.telegramyou.app.telegram.model.GroupMember
import com.telegramyou.app.telegram.model.GroupPermission
import com.telegramyou.app.telegram.model.InviteLink
import com.telegramyou.app.telegram.model.LinkExpiry
import com.telegramyou.app.telegram.model.LinkLimit
import com.telegramyou.app.telegram.model.MemberAction
import com.telegramyou.app.telegram.model.MemberRole
import com.telegramyou.app.telegram.model.sortedTopics
import com.telegramyou.app.ui.failureText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** The member whose admin rights and title are being chosen. */
    val editingAdmin: GroupMember? = null,
    /** What the member search has in it; blank when it is not in use. */
    val memberQuery: String = "",
    /** The server's answer to [memberQuery]; null while there is none. */
    val memberResults: List<GroupMember>? = null,
    val joinRequests: List<JoinRequest>? = null,
    val renamingTopic: ForumTopic? = null,
    val deletingTopic: ForumTopic? = null,
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

    fun loadManagement() = attempt(tr("Could not load the group", "Не удалось загрузить группу")) {
        val management = repository.groupManagement(chatId)
        _uiState.update { it.copy(management = management) }
    }

    /**
     * From a member's menu. Removing asks first, and making or editing an
     * admin opens the rights; everything else is done at once.
     */
    fun onMemberAction(member: GroupMember, action: MemberAction) {
        when (action) {
            MemberAction.Remove -> _uiState.update { it.copy(confirmingRemoval = member) }
            MemberAction.MakeAdmin, MemberAction.EditAdmin -> _uiState.update { it.copy(editingAdmin = member) }
            else -> perform(member, action)
        }
    }

    fun onAdminEditDismissed() = _uiState.update { it.copy(editingAdmin = null) }

    fun onAdminSaved(rights: AdminRights, title: String) {
        val member = _uiState.value.editingAdmin ?: return
        _uiState.update { it.copy(editingAdmin = null) }
        attempt(tr("Could not make them an admin", "Не удалось назначить админом")) {
            repository.promoteMember(chatId, member.user.id, rights, title)
            val management = repository.groupManagement(chatId)
            val name = member.user.displayName
            _uiState.update {
                it.copy(
                    management = management ?: it.management,
                    notice = if (member.role == MemberRole.Admin) tr("$name's rights are saved", "Права $name сохранены") else tr("$name is an admin now", "$name теперь админ")
                )
            }
            refreshSearch()
        }
    }

    private var searching: Job? = null

    /**
     * The member search, asked of the server a moment after typing stops —
     * a big group lists only its recent members, so the local list alone
     * would miss most people.
     */
    fun onMemberQuery(query: String) {
        _uiState.update { it.copy(memberQuery = query, memberResults = if (query.isBlank()) null else it.memberResults) }
        searching?.cancel()
        if (query.isBlank()) return
        searching = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            val found = runCatching { repository.searchGroupMembers(chatId, query) }.getOrNull()
            if (found != null) _uiState.update { it.copy(memberResults = found) }
        }
    }

    private suspend fun refreshSearch() {
        val query = _uiState.value.memberQuery
        if (query.isBlank()) return
        val found = runCatching { repository.searchGroupMembers(chatId, query) }.getOrNull() ?: return
        _uiState.update { it.copy(memberResults = found) }
    }

    fun loadJoinRequests() = attempt(tr("Could not load the requests", "Не удалось загрузить заявки")) {
        val requests = repository.joinRequests(chatId)
        _uiState.update { it.copy(joinRequests = requests) }
    }

    fun onJoinRequest(request: JoinRequest, approve: Boolean) = attempt(tr("Could not answer the request", "Не удалось ответить на заявку")) {
        repository.processJoinRequest(chatId, request.user.id, approve)
        val name = request.user.displayName
        _uiState.update {
            it.copy(
                joinRequests = it.joinRequests?.filter { waiting -> waiting.user.id != request.user.id },
                notice = if (approve) tr("$name joined the group", "$name вступает в группу") else tr("$name's request was declined", "Заявка $name отклонена")
            )
        }
    }

    fun onRemoveConfirmed() {
        val member = _uiState.value.confirmingRemoval ?: return
        _uiState.update { it.copy(confirmingRemoval = null) }
        perform(member, MemberAction.Remove)
    }

    fun onRemoveDismissed() = _uiState.update { it.copy(confirmingRemoval = null) }

    private fun perform(member: GroupMember, action: MemberAction) = attempt(tr("Could not do that", "Не получилось")) {
        repository.applyMemberAction(chatId, member.user.id, action)
        val name = member.user.displayName
        val notice = when (action) {
            MemberAction.MakeAdmin, MemberAction.EditAdmin -> tr("$name is an admin now", "$name теперь админ")
            MemberAction.RemoveAdmin -> tr("$name is no longer an admin", "$name больше не админ")
            MemberAction.Restrict -> tr("$name can't write here now", "$name больше не может здесь писать")
            MemberAction.Unrestrict -> tr("$name can write again", "$name снова может писать")
            MemberAction.Remove -> tr("$name was removed", "$name удалён из группы")
        }
        // Read back rather than guessed: the server may have given the new
        // admin rights of its own, or refused part of it.
        val management = repository.groupManagement(chatId)
        _uiState.update { it.copy(management = management ?: it.management, notice = notice) }
        refreshSearch()
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
                        errorMessage = failureText(tr("Could not change that", "Не удалось изменить"), e.message)
                    )
                }
            }
        }
    }

    fun loadLinks() = attempt(tr("Could not load the links", "Не удалось загрузить ссылки")) {
        val links = repository.inviteLinks(chatId)
        _uiState.update { it.copy(links = links) }
    }

    fun onCreateLink(name: String, expiry: LinkExpiry, limit: LinkLimit, asksFirst: Boolean) = attempt(tr("Could not make the link", "Не удалось создать ссылку")) {
        repository.createInviteLink(chatId, name, expiry.from(System.currentTimeMillis() / 1000), limit.count, asksFirst)
        val links = repository.inviteLinks(chatId)
        _uiState.update { it.copy(links = links, notice = tr("Link created", "Ссылка создана")) }
    }

    fun onRevokeLink(link: InviteLink) = attempt(tr("Could not revoke the link", "Не удалось отозвать ссылку")) {
        repository.revokeInviteLink(chatId, link.link)
        val links = repository.inviteLinks(chatId)
        _uiState.update { it.copy(links = links, notice = tr("Link revoked", "Ссылка отозвана")) }
    }

    /**
     * How many times the topics have been asked for. Only the newest answer
     * is drawn: the list asked for on returning to it could arrive after a
     * topic was made, and put back the list without it.
     */
    private var topicsAsked = 0

    /** The topics, or null when a newer request has been made since. */
    private suspend fun freshTopics(): List<ForumTopic>? {
        val ask = ++topicsAsked
        val topics = sortedTopics(repository.forumTopics(chatId))
        return topics.takeIf { ask == topicsAsked }
    }

    fun loadTopics() = attempt(tr("Could not load the topics", "Не удалось загрузить темы")) {
        freshTopics()?.let { topics -> _uiState.update { it.copy(topics = topics) } }
        // For the topics' menus: only an admin who may manage them has one.
        val management = repository.groupManagement(chatId)
        _uiState.update { it.copy(management = management ?: it.management) }
    }

    /** From a topic's menu. Renaming asks for the name and deleting asks first. */
    fun onTopicAction(topic: ForumTopic, action: TopicAction) {
        when (action) {
            TopicAction.Rename -> _uiState.update { it.copy(renamingTopic = topic) }
            TopicAction.Delete -> _uiState.update { it.copy(deletingTopic = topic) }
            TopicAction.Close, TopicAction.Reopen -> attempt(tr("Could not change the topic", "Не удалось изменить тему")) {
                val closing = action == TopicAction.Close
                repository.setForumTopicClosed(chatId, topic.id, closing)
                reloadTopics(if (closing) tr("${topic.name} is closed", "${topic.name}: тема закрыта") else tr("${topic.name} is open again", "${topic.name}: тема снова открыта"))
            }
        }
    }

    fun onTopicRenamed(name: String) {
        val topic = _uiState.value.renamingTopic ?: return
        _uiState.update { it.copy(renamingTopic = null) }
        attempt(tr("Could not rename the topic", "Не удалось переименовать тему")) {
            repository.renameForumTopic(chatId, topic.id, name)
            reloadTopics(tr("Renamed to ${name.trim()}", "Новое название: ${name.trim()}"))
        }
    }

    fun onTopicDeleteConfirmed() {
        val topic = _uiState.value.deletingTopic ?: return
        _uiState.update { it.copy(deletingTopic = null) }
        attempt(tr("Could not delete the topic", "Не удалось удалить тему")) {
            repository.deleteForumTopic(chatId, topic.id)
            reloadTopics(tr("${topic.name} was deleted", "${topic.name}: тема удалена"))
        }
    }

    fun onTopicDialogDismissed() = _uiState.update { it.copy(renamingTopic = null, deletingTopic = null) }

    private suspend fun reloadTopics(notice: String) {
        val topics = freshTopics()
        _uiState.update { it.copy(topics = topics ?: it.topics, notice = notice) }
    }

    fun onCreateTopic(name: String) = attempt(tr("Could not start the topic", "Не удалось создать тему")) {
        repository.createForumTopic(chatId, name)
        val topics = freshTopics()
        _uiState.update { it.copy(topics = topics ?: it.topics, notice = tr("Topic started", "Тема создана")) }
    }

    fun onNoticeShown() = _uiState.update { it.copy(notice = null) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }
}

/** How long the member search waits after the last keystroke before asking. */
private const val SEARCH_PAUSE_MS = 300L
