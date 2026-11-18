package com.classroom.rfdetector.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DeviceIdentityTest {
    private val resolver = DeviceIdentityResolver(mapOf("001122" to "Registered Vendor"), mapOf(76 to "Apple, Inc.", 117 to "Samsung Electronics"))

    @Test fun macLookupWorksForUniversalUnicastOnly() {
        assertEquals("Registered Vendor", resolver.resolve("00:11:22:33:44:55", null, setOf(ScanSource.WIFI), emptySet()).macOrganization)
        assertNull(resolver.resolve("02:11:22:33:44:55", null, setOf(ScanSource.WIFI), emptySet()).macOrganization)
        assertNull(resolver.resolve("01:11:22:33:44:55", null, setOf(ScanSource.WIFI), emptySet()).macOrganization)
    }

    @Test fun randomAddressCanStillExposeAdvertisingCompany() {
        val identity = resolver.resolve("DA:11:22:33:44:55", "AirPods Pro", setOf(ScanSource.BLE), setOf(76))
        assertEquals(listOf("Apple, Inc."), identity.advertisedCompanies)
        assertNull(identity.macOrganization)
    }

    @Test fun bleMacLookupNeedsAnAddressReportedAsPublic() {
        val address = "00:11:22:33:44:55"
        assertEquals("Registered Vendor", resolver.resolve(address, null, setOf(ScanSource.BLE), emptySet(), BleAddressType.PUBLIC).macOrganization)
        assertNull(resolver.resolve(address, null, setOf(ScanSource.BLE), emptySet(), BleAddressType.RANDOM).macOrganization)
        assertNull(resolver.resolve(address, null, setOf(ScanSource.BLE), emptySet(), BleAddressType.UNKNOWN).macOrganization)
        assertNull(resolver.resolve(address, null, setOf(ScanSource.BLE), emptySet()).macOrganization)
        assertEquals("Registered Vendor", resolver.resolve(address, null, setOf(ScanSource.WIFI), emptySet()).macOrganization)
    }

    @Test fun randomBleAddressesThatCollideWithTheBundledRegistryAreNotNamed() {
        val vendors = File("src/main/assets/mac-vendors.tsv").readLines().associate { it.substringBefore('\t') to it.substringAfter('\t') }
        val bundled = DeviceIdentityResolver(vendors, emptyMap())
        for ((address, vendor) in listOf("58:AD:12:34:56:78" to "Apple, Inc.", "E8:0A:B9:34:56:78" to "Cisco Systems, Inc")) {
            assertEquals(vendor, vendors[address.replace(":", "").take(6)])
            assertNull(bundled.resolve(address, null, setOf(ScanSource.BLE), emptySet(), BleAddressType.RANDOM).macOrganization)
            assertNull(bundled.resolve(address, null, setOf(ScanSource.BLE), emptySet(), BleAddressType.UNKNOWN).macOrganization)
            assertEquals("제조사 미확인", bundled.resolve(address, null, setOf(ScanSource.BLE), emptySet(), BleAddressType.UNKNOWN).summary)
        }
    }

    @Test fun addressTypeIsReadFromTheAddressOnlyWhenItCannotBePublic() {
        assertEquals(BleAddressType.PUBLIC, bleAddressType("02:11:22:33:44:55", BleAddressType.PUBLIC))
        assertEquals(BleAddressType.RANDOM, bleAddressType("00:11:22:33:44:55", BleAddressType.RANDOM))
        assertEquals(BleAddressType.RANDOM, bleAddressType("DA:11:22:33:44:55", BleAddressType.UNKNOWN))
        assertEquals(BleAddressType.RANDOM, bleAddressType("01:11:22:33:44:55", BleAddressType.UNKNOWN))
        assertEquals(BleAddressType.UNKNOWN, bleAddressType("58:AD:12:34:56:78", BleAddressType.UNKNOWN))
        assertEquals(BleAddressType.UNKNOWN, bleAddressType("sim-airpods", BleAddressType.UNKNOWN))
    }

    @Test fun recordedBleRegistryNamesAreNotReusedOnCards() {
        fun signal(source: ScanSource, manufacturer: String) = SnapshotSignal("58:AD:12:34:56:78", source, "BLE 기기 56:78", -60.0, manufacturer)
        assertNull(recordedMaker(signal(ScanSource.BLE, "Apple, Inc. · MAC 등록사")))
        assertNull(recordedMaker(signal(ScanSource.BLE, "제조사 미확인")))
        assertEquals("Apple, Inc. · BLE 광고", recordedMaker(signal(ScanSource.BLE, "Apple, Inc. · BLE 광고")))
        assertEquals("Apple, Inc. · MAC 등록사", recordedMaker(signal(ScanSource.WIFI, "Apple, Inc. · MAC 등록사")))
    }

    @Test fun nameMatchComesFromSpecificBleNameNotMacOrWifiSsid() {
        assertNull(resolver.resolve("00:11:22:33:44:55", null, setOf(ScanSource.BLE), setOf(76)).audioNameMatch)
        assertNull(resolver.resolve("00:11:22:33:44:55", "AirPods Pro", setOf(ScanSource.WIFI), emptySet()).audioNameMatch)
        assertEquals("Sony", resolver.resolve("sim", "Sony WF-1000XM5", setOf(ScanSource.BLE), emptySet()).audioNameMatch!!.brand)
        assertEquals("Samsung", resolver.resolve("sim", "My Galaxy Buds3 Pro", setOf(ScanSource.BLE), setOf(117)).audioNameMatch!!.brand)
    }

    @Test fun unknownModelsAndIdsAreExplicit() {
        val identity = resolver.resolve("sim", "Unknown earbuds", setOf(ScanSource.BLE), setOf(65534))
        assertNull(identity.audioNameMatch)
        assertTrue(identity.advertisedCompanies.single().contains("FFFE"))
    }

    @Test fun companyIdsSurviveAdvertisementsWithoutManufacturerData() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("device", ScanSource.BLE, null, -60, null, false, 1000, setOf(76)))
        tracker.update(RfObservation("device", ScanSource.BLE, "AirPods", -59, null, false, 1100))
        assertEquals(setOf(76), tracker.snapshot(1100).single().manufacturerIds)
    }

    @Test fun refreshedAssignmentsResolveThroughTheBundledRegistries() {
        val vendors = File("src/main/assets/mac-vendors.tsv").readLines().associate { it.substringBefore('\t') to it.substringAfter('\t') }
        val companies = File("src/main/assets/bluetooth-companies.tsv").readLines().associate { it.substringBefore('\t').toInt() to it.substringAfter('\t') }
        val bundled = DeviceIdentityResolver(vendors, companies)
        assertEquals("Microsoft Corporation", bundled.resolve("64:2F:4F:00:00:01", null, setOf(ScanSource.WIFI), emptySet()).macOrganization)
        assertNull(bundled.resolve("66:2F:4F:00:00:01", null, setOf(ScanSource.WIFI), emptySet()).macOrganization)
        assertEquals(listOf("LightingSoft AG", "AutoStoa LLC"),
            bundled.resolve("DA:11:22:33:44:55", null, setOf(ScanSource.BLE), setOf(4430, 4429)).advertisedCompanies)
    }

    @Test fun bundledOfficialRegistriesAreWellFormed() {
        val vendors = File("src/main/assets/mac-vendors.tsv").readLines().associate { it.substringBefore('\t') to it.substringAfter('\t') }
        val companies = File("src/main/assets/bluetooth-companies.tsv").readLines().associate { it.substringBefore('\t').toInt() to it.substringAfter('\t') }
        assertTrue(vendors.size >= 40_000)
        assertTrue(vendors.keys.all { it.matches(Regex("[A-F0-9]{6}")) })
        assertEquals("Apple, Inc.", companies[76])
        assertTrue(companies[117]!!.contains("Samsung"))
        assertTrue(companies.size >= 4000)
    }
}
