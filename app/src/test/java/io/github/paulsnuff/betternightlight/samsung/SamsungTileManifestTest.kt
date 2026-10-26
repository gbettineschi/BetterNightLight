package io.github.paulsnuff.betternightlight.samsung

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SamsungTileManifestTest {
    @Test fun tileUsesPanelListeningSoExternalChangesRefreshOnOpening() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val services = document.getElementsByTagName("service")
        val android = "http://schemas.android.com/apk/res/android"
        val service = (0 until services.length).map { services.item(it) as org.w3c.dom.Element }
            .single { it.getAttributeNS(android, "name").endsWith(".SamsungTileService") }
        val metadata = service.getElementsByTagName("meta-data")
        assertFalse((0 until metadata.length).any {
            val item = metadata.item(it) as org.w3c.dom.Element
            item.getAttributeNS(android, "name") == "android.service.quicksettings.ACTIVE_TILE" && item.getAttributeNS(android, "value") == "true"
        })
    }
}
