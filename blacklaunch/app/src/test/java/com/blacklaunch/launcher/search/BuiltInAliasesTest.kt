package com.blacklaunch.launcher.search

import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInAliasesTest {
    private val packagePattern = Regex("^[a-zA-Z][\\w]*(\\.[a-zA-Z_][\\w]*)+$")

    @Test fun keysArePackageNames() {
        BuiltInAliases.byPackage.keys.forEach {
            assertTrue("не схоже на пакет: $it", packagePattern.matches(it))
        }
    }

    @Test fun aliasesAreNotBlankAfterNormalization() {
        (BuiltInAliases.byPackage.values.flatten() + BuiltInAliases.groups.flatten()).forEach {
            assertTrue("порожній псевдонім: '$it'", SearchNormalizer.normalize(it).isNotEmpty())
        }
    }

    @Test fun noDuplicateAliasesWithinOneApp() {
        BuiltInAliases.byPackage.forEach { (pkg, aliases) ->
            val normalized = aliases.map(SearchNormalizer::normalize)
            assertTrue("дублікати в $pkg: $aliases", normalized.size == normalized.toSet().size)
        }
    }
}
