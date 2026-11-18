package com.infinity.drive.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioPlaylist(
    val id: String,
    val chatId: Long,
    val name: String,
    val trackIds: List<String>
)

fun List<String>.movePlaylistTrack(id: String, direction: Int): List<String> {
    val from = indexOf(id)
    val to = from + direction
    if (from !in indices || to !in indices || direction !in listOf(-1, 1)) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
