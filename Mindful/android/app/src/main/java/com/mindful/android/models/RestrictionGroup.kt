package com.mindful.android.models

import com.mindful.android.utils.JsonUtils
import org.json.JSONObject

/**
 * Represents a group of restricted apps with shared usage timer settings.
 */
data class RestrictionGroup(
    /**
     * Unique ID of the group.
     */
    val id: Int = 0,

    /**
     * Name of the group (e.g., "Social", "Games").
     */
    val groupName: String = "Social",

    /**
     * Time limit for all apps in the group, in seconds.
     */
    val timerSec: Int = 0,

    /**
     * Set of app package names associated with this group.
     */
    val distractingApps: Set<String> = emptySet(),
) {
    companion object {

        /**
         * Constructs a [RestrictionGroup] from a JSON string.
         */
        fun fromJson(json: String): RestrictionGroup {
            val jsonObject = JSONObject(json)
            return RestrictionGroup(
                id = jsonObject.optInt("id", 0),
                groupName = jsonObject.optString("groupName", "Social"),
                timerSec = jsonObject.optInt("timerSec", 0),
                distractingApps = JsonUtils.parseStringSet(
                    jsonObject.optJSONArray("distractingApps")?.toString()
                )
            )
        }
    }
}
