package com.kumpali.storax

import java.io.File

data class TrashEntry(
    val id: String,                 // unique trash id
    val name: String,               // file/folder name
    val isSaf: Boolean,             // native or SAF
    val trashedAt: Long,            // timestamp

    // Native
    val originalPath: String? = null,
    val trashedPath: String? = null,

    // SAF
    val originalUri: String? = null,
    val trashedUri: String? = null,
    val safRootUri: String? = null
)
fun TrashEntry.toMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "name" to name,
        "isSaf" to isSaf,
        "trashedAt" to trashedAt,
        "originalPath" to originalPath,
        "trashedPath" to trashedPath,
        "originalUri" to originalUri,
        "trashedUri" to trashedUri,
        "safRootUri" to safRootUri
    )

fun trashEntryFromMap(map: Map<String, Any?>): TrashEntry =
    TrashEntry(
        id = map["id"] as String,
        name = map["name"] as String,
        isSaf = map["isSaf"] as Boolean,
        trashedAt = (map["trashedAt"] as Number).toLong(),
        originalPath = map["originalPath"] as String?,
        trashedPath = map["trashedPath"] as String?,
        originalUri = map["originalUri"] as String?,
        trashedUri = map["trashedUri"] as String?,
        safRootUri = map["safRootUri"] as String?
    )
