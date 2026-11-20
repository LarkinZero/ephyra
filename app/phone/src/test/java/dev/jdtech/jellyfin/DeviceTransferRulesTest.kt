package dev.jdtech.jellyfin

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * CE (#731): a device-to-device transfer must leave out what a cloud backup leaves out, the access
 * tokens and the MDBList key. Without a `<device-transfer>` section Android 12+ copies everything.
 */
class DeviceTransferRulesTest {
    private val rules =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/data_extraction_rules.xml"))
            .documentElement

    private fun excludes(section: String): Set<Pair<String, String>> {
        val sections = rules.getElementsByTagName(section)
        assertEquals(1, sections.length, message = "one <$section> section")
        val excludes = (sections.item(0) as Element).getElementsByTagName("exclude")
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
