package com.blacklaunch.launcher.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameStartsTest {
    @Test fun wordStartsInBothScripts() {
        assertTrue(NameStarts.matches("Олег Миколайович", PreparedQuery("ол")))
        assertTrue(NameStarts.matches("Олег Петренко", PreparedQuery("пе ол")))
        assertTrue(NameStarts.matches("Petro", PreparedQuery("пе")))
        assertTrue(NameStarts.matches("Петро", PreparedQuery("pe")))
    }

    @Test fun lettersSomewhereElseDoNotMatch() {
        assertFalse(NameStarts.matches("Олег Миколайович", PreparedQuery("по")))
        assertFalse(NameStarts.matches("Миколайович", PreparedQuery("ай")))
    }
}
