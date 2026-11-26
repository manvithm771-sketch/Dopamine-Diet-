package com.mindful.android.models

import com.mindful.android.enums.PlatformFeatures
import com.mindful.android.utils.JsonUtils
import org.json.JSONObject

/**
 * Represents the wellbeing configuration for the application.
 */
data class Wellbeing(

    /**
     * Whether adult (NSFW) sites are blocked.
     */
    val blockNsfwSites: Boolean = false,

    /** Allowed short-form content time in milliseconds. */
    val allowedShortsTimeMs: Long = 30L * 60L * 1000L,

    /** Platform surfaces whose short-form usage is counted. */
    val blockedFeatures: Set<PlatformFeatures> = emptySet(),

    /**
     * Set of blocked website hosts.
     */
    val blockedWebsites: Set<String> = emptySet(),

    /**
     * Set of NSFW website hosts.
     */
    val nsfwWebsites: Set<String> = emptySet(),
) {
    companion object {
        /**
         * Builds a [Wellbeing] object from a JSON string (typically from Flutter).
         */
        fun fromJson(json: String): Wellbeing {
            val jsonObject = JSONObject(json)

            return Wellbeing(
                blockNsfwSites = jsonObject.optBoolean("blockNsfwSites", false),
                allowedShortsTimeMs = secondsToMillis(jsonObject.optLong("allowedShortsTimeSec", 1800L)),
                blockedFeatures = JsonUtils.parseStringSet(
                    jsonObject.optJSONArray("blockedFeatures")?.toString()
                ).mapNotNull(PlatformFeatures::fromName).toSet(),
                blockedWebsites = JsonUtils.parseStringSet(
                    jsonObject.optJSONArray("blockedWebsites")?.toString()
                ),
                nsfwWebsites = JsonUtils.parseStringSet(
                    jsonObject.optJSONArray("nsfwWebsites")?.toString()
                ),
            )
        }
    }
}

private fun secondsToMillis(seconds: Long): Long = when {
    seconds > Long.MAX_VALUE / 1000L -> Long.MAX_VALUE
    seconds < Long.MIN_VALUE / 1000L -> Long.MIN_VALUE
    else -> seconds * 1000L
}
