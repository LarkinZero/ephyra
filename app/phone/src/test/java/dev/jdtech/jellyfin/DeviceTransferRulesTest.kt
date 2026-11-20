package dev.jdtech.jellyfin

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * CE (#731): a device-to-device transfer must leave out what a cloud backup leaves out, the access
 * tokens, the MDBList key and the downloads that cannot be used without the servers database.
 * Without a `<device-transfer>` section Android 12+ copies everything.
 */
class DeviceTransferRulesTest {
    private val rules =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/data_extraction_rules.xml"))
            .documentElement

    /** Android 11 and earlier: one set of rules for both the backup and the transfer. */
    private val legacyRules =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/backup_rules.xml"))
            .documentElement

    private fun excludes(section: String): Set<Pair<String, String>> {
        val sections = rules.getElementsByTagName(section)
        assertEquals(1, sections.length, message = "one <$section> section")
        return excludesOf(sections.item(0) as Element)
    }

    private fun excludesOf(element: Element): Set<Pair<String, String>> {
        val excludes = element.getElementsByTagName("exclude")
        return (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .map { it.getAttribute("domain") to it.getAttribute("path") }
            .toSet()
    }

    @Test
    fun `a device transfer leaves out what a cloud backup leaves out`() {
        assertEquals(excludes("cloud-backup"), excludes("device-transfer"))
    }

    @Test
    fun `Android 11 and earlier leave out the same`() {
        assertEquals(excludes("device-transfer"), excludesOf(legacyRules))
    }

    @Test
    fun `a device transfer leaves out the downloads, which are of no use without their index`() {
        val excluded = excludes("device-transfer")
        for (rule in
            listOf(
                "external" to "downloads",
                "file" to "images",
                "file" to "trickplay",
                "file" to "home-cache",
            )) {
            assertTrue(rule in excluded, message = "$rule must not be transferred")
        }
    }

    @Test
    fun `a device transfer leaves out the servers database and the preferences`() {
        val excluded = excludes("device-transfer")
        for (rule in
            listOf(
                "database" to "servers",
                "database" to "servers-wal",
                "database" to "servers-shm",
                "sharedpref" to ".",
            )) {
            assertTrue(rule in excluded, message = "$rule must not be transferred")
        }
    }
}
