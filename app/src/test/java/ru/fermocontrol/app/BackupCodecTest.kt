package ru.fermocontrol.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupCodecTest {
    private fun sample() = Product(id = "fixed-id", number = 1, name = "Продукт №1",
        basis = "Говядина", spices = listOf("Перец", "Чеснок"), casing = "Без оболочки",
        initialWeight = 1000.0, targetLoss = 30.0, startedAt = 1000L,
        weighings = listOf(Weighing(850.0, 2000L, mapOf("Запах" to "Обычный"), "Заметка")))
    private fun json() = JSONObject(String(BackupCodec.encode(listOf(sample())), Charsets.UTF_8))
    private fun bytes(root: JSONObject) = root.toString().toByteArray(Charsets.UTF_8)
    private fun reject(block: () -> Unit) {
        try { block(); fail("Expected invalid file to be rejected") }
        catch (e: Exception) { /* JUnit AssertionError is not an Exception. */ }
    }
    @Test fun roundTripPreservesEverything() {
        assertEquals(listOf(sample()), BackupCodec.decode(BackupCodec.encode(listOf(sample()))))
    }
    @Test fun emptyBackupWorks() { assertTrue(BackupCodec.decode(BackupCodec.encode(emptyList())).isEmpty()) }
    @Test fun archiveAndReusedNumberWork() {
        val all = listOf(sample().copy(archivedAt = 3000L), sample().copy(id = "new"))
        assertEquals(all, BackupCodec.decode(BackupCodec.encode(all)))
    }
    @Test fun utf8BomIsAccepted() {
        val bytes = "\uFEFF".toByteArray() + BackupCodec.encode(listOf(sample()))
        assertEquals(listOf(sample()), BackupCodec.decode(bytes))
    }
    @Test fun unknownVersionIsRejected() { reject { BackupCodec.decode(bytes(json().put("schemaVersion", 2))) } }
    @Test fun fractionalVersionIsRejected() { reject { BackupCodec.decode(bytes(json().put("schemaVersion", 1.5))) } }
    @Test fun fractionalNumberIsRejected() {
        val root = json(); root.getJSONArray("products").getJSONObject(0).put("number", 1.5)
        reject { BackupCodec.decode(bytes(root)) }
    }
    @Test fun duplicateIdsAreRejected() { reject { BackupCodec.encode(listOf(sample(), sample().copy(number = 2))) } }
    @Test fun duplicateActiveNumbersAreRejected() { reject { BackupCodec.encode(listOf(sample(), sample().copy(id = "new"))) } }
    @Test fun zeroWeightIsRejected() { reject { BackupCodec.encode(listOf(sample().copy(initialWeight = 0.0))) } }
    @Test fun negativeTimestampIsRejected() { reject { BackupCodec.encode(listOf(sample().copy(startedAt = -1L))) } }
    @Test fun missingNameIsRejected() {
        val root = json(); root.getJSONArray("products").getJSONObject(0).remove("name")
        reject { BackupCodec.decode(bytes(root)) }
    }
    @Test fun truncatedJsonIsRejected() { reject { BackupCodec.decode("{\"schemaVersion\":1".toByteArray()) } }
    @Test fun extraRootIsRejected() { reject { BackupCodec.decode(BackupCodec.encode(emptyList()) + "{}".toByteArray()) } }
    @Test fun invalidUtf8IsRejected() { reject { BackupCodec.decode(byteArrayOf(0xC3.toByte(), 0x28)) } }
    @Test fun notesMayBeAbsentInOldFiles() {
        val root = json()
        root.getJSONArray("products").getJSONObject(0).getJSONArray("weighings").getJSONObject(0).remove("note")
        assertEquals("", BackupCodec.decode(bytes(root)).single().weighings.single().note)
    }
    @Test fun boundedInputReadsBytes() {
        val bytes = BackupCodec.encode(listOf(sample()))
        assertArrayEquals(bytes, BackupCodec.read(ByteArrayInputStream(bytes)))
    }
    @Test fun oversizedInputIsRejected() {
        reject { BackupCodec.read(ByteArrayInputStream(ByteArray(BackupCodec.MAX_BYTES + 1))) }
    }
}
