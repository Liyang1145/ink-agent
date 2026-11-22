package com.xnotes.format

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipTailAssetPrefixTest {
    @get:Rule val temp = TemporaryFolder()

    private val first = "assets/image-000.png"
    private val second = "assets/image-001.png"
    private val manifest = "manifest.json"
    private val minimum = 1L shl 20

    @Test fun refusesPositionalSwapOfDifferentEqualLengthAssets() {
        val a = asset(1)
        val b = asset(2)
        val existing = bundle(listOf(first to a, second to b), listOf(manifest to "old"))
        val tail = bundle(emptyList(), listOf(manifest to "new"))
        assertNull(match(existing, listOf(first to b, second to a), tail))
    }

    @Test fun refusesEqualLengthReplacementAndStructuralChanges() {
        val a = asset(1)
        val b = asset(2)
        val original = listOf(first to a, second to b)
        val existing = bundle(original, listOf(manifest to "old"))
        val tail = bundle(emptyList(), listOf(manifest to "new"))
        assertNull(match(existing, listOf(first to asset(3), second to b), tail))
        assertNull(match(existing, original.dropLast(1), tail))
        val newFlow = bundle(emptyList(), listOf("flow.xml" to "flow", manifest to "new"))
        assertNull(match(existing, original, newFlow))
    }

    @Test fun identicalAssetsAllowSpliceAndReopenWithNewManifest() {
        val original = listOf(first to asset(1), second to asset(2))
        val existing = bundle(original, listOf(manifest to "old"))
        val tail = bundle(emptyList(), listOf(manifest to "new"))
        val freshFiles = original.map { (name, file) -> name to asset(file.readBytes()[0]) }
        assertNotNull(match(existing, freshFiles, tail))
        RandomAccessFile(existing, "rw").use { dest ->
            tail.inputStream().use { replacement ->
                val existingDir = requireNotNull(ZipTail.read(dest.channel))
                val tailDir = requireNotNull(ZipTail.read(replacement.channel))
                val start = requireNotNull(
                    ZipTail.matchingAssetPrefix(dest.channel, existingDir.entries, freshFiles, tailDir.entries, minimum)
                )
                assertTrue(start >= minimum)
                assertTrue(ZipTail.splice(dest.channel, start, existingDir.entries.take(original.size), replacement.channel, tailDir) > 0)
            }
        }
        ZipFile(existing).use { result ->
            for ((name, file) in original) {
                assertArrayEquals(file.readBytes(), result.getInputStream(result.getEntry(name)).use { it.readBytes() })
            }
            assertArrayEquals("new".toByteArray(), result.getInputStream(result.getEntry(manifest)).use { it.readBytes() })
        }
    }

    private fun asset(value: Byte): File =
        temp.newFile().apply { writeBytes(ByteArray(600 * 1024) { value }) }

    private fun match(existing: File, sources: List<Pair<String, File>>, tail: File): Long? =
        existing.inputStream().use { old ->
            tail.inputStream().use { replacement ->
                val existingDir = requireNotNull(ZipTail.read(old.channel))
                val tailDir = requireNotNull(ZipTail.read(replacement.channel))
                ZipTail.matchingAssetPrefix(old.channel, existingDir.entries, sources, tailDir.entries, minimum)
            }
        }

    private fun bundle(stored: List<Pair<String, File>>, tail: List<Pair<String, String>>): File =
        temp.newFile().apply {
            ZipOutputStream(outputStream()).use { zip ->
                for ((name, file) in stored) {
                    val bytes = file.readBytes()
                    zip.putNextEntry(ZipEntry(name).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    })
                    zip.write(bytes)
                    zip.closeEntry()
                }
                for ((name, value) in tail) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray())
                    zip.closeEntry()
                }
            }
        }
}
