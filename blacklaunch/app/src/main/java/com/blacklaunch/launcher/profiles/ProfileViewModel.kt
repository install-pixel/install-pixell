package com.blacklaunch.launcher.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.launcher.apps.AppInfo
import com.blacklaunch.launcher.apps.AppSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Керування профілями в налаштуваннях. Кожна зміна зберігається одразу. */
class ProfileViewModel(
    private val repository: ProfileRepository,
    appSource: AppSource,
) : ViewModel() {
    enum class Message { SAVE_FAILED, LAST_PROFILE }

    val profiles: StateFlow<List<LauncherProfile>> =
        repository.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeId: StateFlow<String?> =
        repository.activeProfile.map { it?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val apps: StateFlow<List<AppInfo>> = appSource.apps

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: ReceiveChannel<Message> get() = _messages

    fun setActive(id: String) = repository.setActive(id)

    fun create(name: String, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.create(name)
                .onSuccess(onCreated)
                .onFailure { _messages.trySend(Message.SAVE_FAILED) }
        }
    }

    fun createFrom(template: (String) -> LauncherProfile, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repository.createFrom(template)
                .onSuccess(onCreated)
                .onFailure { _messages.trySend(Message.SAVE_FAILED) }
        }
    }

    fun update(profile: LauncherProfile) {
        val position = profiles.value.indexOfFirst { it.id == profile.id }.takeIf { it >= 0 } ?: profiles.value.size
        viewModelScope.launch {
            repository.save(profile, position).onFailure { _messages.trySend(Message.SAVE_FAILED) }
        }
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
                .onSuccess { onDeleted() }
                .onFailure {
                    _messages.trySend(
                        if (it is ProfileRepository.LastProfileException) Message.LAST_PROFILE else Message.SAVE_FAILED,
                    )
                }
        }
    }
}
