package com.mindful.android.nativeapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeGroupAppLabelsTest {
    @Test fun displaysInstalledLabelsAndFallsBackForMissingApps() {
        assertEquals(
            "Social Network, com.example.missing, Video Player",
            nativeGroupAppLabels(
                listOf(
                    "com.example.social",
                    "com.example.missing",
                    "com.example.video"
                ),
                mapOf(
                    "com.example.social" to "Social Network",
                    "com.example.video" to "Video Player"
                )
            )
        )
    }

    @Test fun removesDuplicatePackagesAndShowsNoneForEmptyGroups() {
        val labels = mapOf("com.example.social" to "Social Network")

        assertEquals(
            "Social Network",
            nativeGroupAppLabels(
                listOf("com.example.social", "com.example.social"),
                labels
            )
        )
        assertEquals("None", nativeGroupAppLabels(emptyList(), labels))
    }
}
