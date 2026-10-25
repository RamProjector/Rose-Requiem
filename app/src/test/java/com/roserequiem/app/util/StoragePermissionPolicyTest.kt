package com.roserequiem.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePermissionPolicyTest {
    @Test
    fun usesLegacyStoragePermissionFlowBeforeAndroid11() {
        assertFalse(usesAllFilesAccessPermission(23))
        assertFalse(usesAllFilesAccessPermission(29))
    }

    @Test
    fun usesAllFilesAccessFlowStartingOnAndroid11() {
        assertTrue(usesAllFilesAccessPermission(30))
        assertTrue(usesAllFilesAccessPermission(31))
        assertTrue(usesAllFilesAccessPermission(33))
        assertTrue(usesAllFilesAccessPermission(36))
        assertTrue(usesAllFilesAccessPermission(37))
    }
}
