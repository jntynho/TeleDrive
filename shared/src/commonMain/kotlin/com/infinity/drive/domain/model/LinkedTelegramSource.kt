package com.infinity.drive.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LinkedTelegramSource(val driveChatId: Long, val sourceChatId: Long, val title: String)
