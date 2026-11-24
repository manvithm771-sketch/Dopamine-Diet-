package com.mindful.android.nativeapp.data.wellbeing

import com.mindful.android.models.Wellbeing
import com.mindful.android.nativeapp.data.local.entity.NativeWellbeingEntity
import org.json.JSONArray

internal fun toNativeWellbeingModel(entity: NativeWellbeingEntity?): Wellbeing {
    if (entity == null) return Wellbeing()
    return Wellbeing(
        blockNsfwSites = entity.blockNsfwSites,
        allowedShortsTimeMs = secondsToMillis(entity.allowedShortsTimeSec),
        blockedFeatures = parseFeatures(entity.blockedFeaturesJson),
        blockedWebsites = parseHosts(entity.blockedWebsitesJson),
        nsfwWebsites = parseHosts(entity.nsfwWebsitesJson)
    )
}

private fun secondsToMillis(seconds: Long): Long = when {
    seconds > Long.MAX_VALUE / 1000L -> Long.MAX_VALUE
    seconds < Long.MIN_VALUE / 1000L -> Long.MIN_VALUE
    else -> seconds * 1000L
}

private fun parseFeatures(raw: String): Set<com.mindful.android.enums.PlatformFeatures> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        com.mindful.android.enums.PlatformFeatures.fromName(array.getString(index).trim())
    }.toSet()
}.getOrDefault(emptySet())

private fun parseHosts(raw: String): Set<String> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        array.getString(index).trim().lowercase().takeIf { it.isNotBlank() }
    }.toSet()
}.getOrDefault(emptySet())
