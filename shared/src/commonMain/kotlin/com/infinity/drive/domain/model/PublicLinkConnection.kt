package com.infinity.drive.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PublicLinkConnection(val origin: String, val ownerToken: String) {
    override fun toString(): String = "PublicLinkConnection(origin=$origin)"
}
