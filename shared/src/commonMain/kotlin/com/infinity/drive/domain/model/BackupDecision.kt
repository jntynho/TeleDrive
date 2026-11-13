package com.infinity.drive.domain.model

enum class BackupDecision {
    BACKUP,
    SKIP_UNCHANGED,
    SKIP_EXCLUDED,
    SKIP_TOO_LARGE,
    SKIP_TOO_SMALL,
    SKIP_DUPLICATE
}
