package com.blacklaunch.diary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleOut
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
            // Полиця ↔ щоденник — м'яко: старе плавно тане й ледь віддаляється, нове проступає з легкого наближення.
            transitionSpec = {
                (fadeIn(tween(520, delayMillis = 140, easing = FastOutSlowInEasing)) + scaleIn(tween(620, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(380, easing = FastOutSlowInEasing)) + scaleOut(tween(420, easing = FastOutSlowInEasing), targetScale = 1.03f))
            },
            label = "shelf",
        ) { id ->
            val info = vm.info(id)
            if (info == null) {
                ShelfScreen(vm, onLanguageChanged, onExit)
            } else {
                BookScreen(
                    vm = vm,
                    info = info,
                    // Полиця є завжди (навіть з одним щоденником): там новий щоденник, копія й загальні налаштування.
                    onShelf = { vm.closeDiary(); vm.current = null },
                    onLanguageChanged = onLanguageChanged,
                )
            }
        }
        val n = vm.notice
        Banner(n?.let { stringResource(it.text) }, action = if (n?.undo != null) stringResource(R.string.undo_action) else null, danger = n?.danger == true) {
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

