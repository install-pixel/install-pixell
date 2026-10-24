package com.blacklaunch.launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomCommandsTest {
    private val home = CustomCommand("1", "Я вдома", listOf("вайфай", "яскравість 30", "режим дім"))
    private val sleep = CustomCommand("2", "На ніч", listOf("яскравість 0", "без звуку"))

    @Test fun exactMatchIgnoresCaseAndPunctuation() {
        assertEquals(home, CustomCommandMatcher.exact(listOf(home, sleep), "я вдома!"))
        assertNull(CustomCommandMatcher.exact(listOf(home, sleep), "я вдома зараз"))
    }

    @Test fun suggestByStartOfPhrase() {
        assertEquals(listOf(home), CustomCommandMatcher.suggest(listOf(home, sleep), "я вд"))
        assertEquals(emptyList<CustomCommand>(), CustomCommandMatcher.suggest(listOf(home, sleep), "на"))
    }

    @Test fun encodeDecodeRoundTripAndEdit() {
        val store = InMemoryCustomCommandStore()
        store.save(home)
        store.save(sleep)
        store.save(home.copy(steps = listOf("вайфай")))
        val decoded = InMemoryCustomCommandStore.decode(InMemoryCustomCommandStore.encode(store.commands.value))
        assertEquals(listOf(home.copy(steps = listOf("вайфай")), sleep), decoded)
        store.delete("2")
        assertEquals(listOf("1"), store.commands.value.map { it.id })
    }

    @Test fun emptyStepsAreDropped() {
        assertEquals(listOf("вайфай", "режим дім"), CustomCommand.create(" Я вдома ", listOf(" вайфай ", "", "режим дім")).steps)
    }
}
