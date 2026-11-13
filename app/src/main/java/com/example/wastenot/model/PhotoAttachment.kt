package com.example.wastenot.model

import java.time.Instant

data class PhotoAttachment(
    val id: String,
    val mimeType: String,
    val sizeBytes: Int,
    val previewUrl: String,
    val createdAt: Instant,
)
