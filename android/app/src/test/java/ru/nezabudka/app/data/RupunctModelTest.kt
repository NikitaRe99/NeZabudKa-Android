package ru.nezabudka.app.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class RupunctModelTest {
    @Test fun `HTML or incomplete download is rejected`() {
        val file = File.createTempFile("rupunct", ".part")
        try {
            file.writeText("<html>Error</html>")
            val error = runCatching { RupunctModel.validate(file) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        } finally { file.delete() }
    }

    @Test fun `a file of the correct size still requires a matching checksum`() {
        val file = File.createTempFile("rupunct", ".part")
        try {
            RandomAccessFile(file, "rw").use { it.setLength(RupunctModel.BYTES) }
            val error = runCatching { RupunctModel.validate(file) }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("Контрольная сумма"))
        } finally { file.delete() }
    }
}
