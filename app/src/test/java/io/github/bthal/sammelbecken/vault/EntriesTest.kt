package io.github.bthal.sammelbecken.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class EntriesTest {

    @Test fun textEntryIsChecklistItem() {
        assertEquals("- [ ] buy milk", Entries.textEntry("  buy milk \n"))
    }

    @Test fun multiLineTextIsIndented() {
        assertEquals("- [ ] first\n  second", Entries.textEntry("first\nsecond"))
    }

    @Test fun audioEntryEmbedsPath() {
        assertEquals("- [ ] ![[Audio/2026-10-09_143201.m4a]]", Entries.audioEntry("Audio/2026-10-09_143201.m4a"))
    }

    @Test fun audioFileNameUsesTimestamp() {
        assertEquals("2026-10-09_143201.m4a", Entries.audioFileName(LocalDateTime.of(2026, 10, 9, 14, 32, 1)))
    }

    @Test fun appendChunkAddsNewlineOnlyWhenNeeded() {
        assertEquals("- [ ] a\n", Entries.appendChunk("- [ ] a", null))
        assertEquals("- [ ] a\n", Entries.appendChunk("- [ ] a", '\n'))
        assertEquals("\n- [ ] a\n", Entries.appendChunk("- [ ] a", 'x'))
    }

    @Test fun relativePathInsideVault() {
        assertEquals("Inbox/Sammel.md", VaultPaths.relativePath("primary:Documents/Vault", "primary:Documents/Vault/Inbox/Sammel.md"))
        assertEquals("", VaultPaths.relativePath("primary:Documents/Vault", "primary:Documents/Vault"))
    }

    @Test fun relativePathAtStorageRoot() {
        assertEquals("Vault/Note.md", VaultPaths.relativePath("primary:", "primary:Vault/Note.md"))
    }

    @Test fun relativePathOutsideVaultIsNull() {
        assertNull(VaultPaths.relativePath("primary:Documents/Vault", "primary:Documents/VaultOther/x.md"))
        assertNull(VaultPaths.relativePath("primary:Documents/Vault", "1234-5678:Documents/Vault/x.md"))
    }

    @Test fun joinSkipsEmptySegments() {
        assertEquals("Audio/x.m4a", VaultPaths.join("", "Audio/", "x.m4a"))
        assertEquals("x.m4a", VaultPaths.join("", "x.m4a"))
    }
}
