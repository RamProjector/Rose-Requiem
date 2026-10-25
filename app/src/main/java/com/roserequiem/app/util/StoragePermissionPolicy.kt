package com.roserequiem.app.util

/**
 * Whether the app's current legacy onboarding flow checks the special all-files
 * access grant. Android 11 (API 30) introduced this permission, so API 30 is
 * included rather than treated as a pre-Android-11 release.
 */
internal fun usesAllFilesAccessPermission(sdkInt: Int): Boolean = sdkInt >= 30
