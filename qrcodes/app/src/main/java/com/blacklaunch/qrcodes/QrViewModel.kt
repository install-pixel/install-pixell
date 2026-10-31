package com.blacklaunch.qrcodes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.qrcodes.data.QrCode
import com.blacklaunch.qrcodes.data.QrDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Що сталося після «Зберегти». */
enum class SaveResult { Saved, AlreadySaved }

class QrViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = QrDatabase.get(app).codes()

    val all: StateFlow<List<QrCode>?> = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val favorites: StateFlow<List<QrCode>?> = dao.favorites().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Зберігає код. Такий самий зміст уже є — не дублюємо, а піднімаємо його нагору списку.
     */
    fun save(content: String, onDone: (SaveResult) -> Unit) {
        if (content.isEmpty()) return
        viewModelScope.launch {
            val existing = dao.byContent(content)
            if (existing != null) {
                dao.update(existing.copy(createdAt = System.currentTimeMillis()))
                onDone(SaveResult.AlreadySaved)
            } else {
                dao.insert(QrCode(content = content))
                onDone(SaveResult.Saved)
            }
        }
    }

    fun toggleFavorite(code: QrCode) = viewModelScope.launch {
        dao.update(code.copy(favoriteAt = if (code.isFavorite) null else System.currentTimeMillis()))
    }

    /** Вибрані в новому порядку (після перетягування на головному). */
    fun reorderFavorites(ids: List<Long>) = viewModelScope.launch { dao.reorderFavorites(ids) }

    /** Прибрати з вибраного (сам код лишається серед збережених). */
    fun unfavorite(code: QrCode) = viewModelScope.launch { dao.setFavoriteAt(code.id, null) }

    /** «Повернути» — на те саме місце серед вибраних. */
    fun refavorite(code: QrCode) = viewModelScope.launch { dao.setFavoriteAt(code.id, code.favoriteAt ?: System.currentTimeMillis()) }

    /** Своя назва; порожня — знову підписується змістом. */
    fun rename(code: QrCode, name: String) = viewModelScope.launch {
        dao.update(code.copy(name = name.trim().ifEmpty { null }))
    }

    fun delete(code: QrCode) = viewModelScope.launch { dao.delete(code.id) }

    /** «Повернути» після видалення — той самий запис (з назвою, вибраним і датою). */
    fun restore(code: QrCode) = viewModelScope.launch { dao.insert(code) }
}
