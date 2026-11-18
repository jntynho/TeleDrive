package com.infinity.drive.core.files

data class LocalCleanup(
    val deletedCount: Int,
    val consentRequest: DeleteConsentRequest? = null,
    val keptCount: Int = 0,
    val consentCopies: Map<String, String> = emptyMap()
)
