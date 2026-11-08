package com.blacklaunch.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.blacklaunch.diary.book.BookScreen
import com.blacklaunch.diary.shelf.ShelfScreen
import com.blacklaunch.diary.ui.Banner
import com.blacklaunch.diary.ui.theme.Oled

/**
 * Корінь програми: полиця (коли щоденників кілька) або один щоденник на весь екран. Сповіщення — одне місце вгорі.
 */
@Composable
fun DiaryApp(vm: DiaryViewModel, onExit: () -> Unit, onLanguageChanged: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Oled.Black)) {
        val current = vm.info(vm.current)
        AnimatedContent(
            targetState = current?.id,
            transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.94f)) togetherWith fadeOut(tween(200)) },
            label = "shelf",
        ) { id ->
            val info = vm.info(id)
            if (info == null) {
                ShelfScreen(vm, onLanguageChanged)
            } else {
                BookScreen(
                    vm = vm,
                    info = info,
                    onShelf = if (vm.shelf.size > 1) { { vm.closeDiary(); vm.current = null } } else null,
                    onExit = onExit,
                    onLanguageChanged = onLanguageChanged,
                )
            }
        }
        val n = vm.notice
        Banner(n?.let { stringResource(it.text) }, action = if (n?.undo != null) stringResource(R.string.undo_action) else null) {
            n?.undo?.invoke()
            vm.notice = null
        }
        if (vm.busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                com.blacklaunch.diary.ui.Notice(stringResource(R.string.working), Modifier.navigationBarsPadding().padding(20.dp))
            }
        }
    }
}

