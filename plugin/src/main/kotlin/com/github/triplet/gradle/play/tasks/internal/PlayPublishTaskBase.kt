package com.github.triplet.gradle.play.tasks.internal

import com.android.build.gradle.api.ApplicationVariant
import com.github.triplet.gradle.play.PlayPublisherExtension
import com.github.triplet.gradle.play.internal.EDIT_ID_FILE
import com.github.triplet.gradle.play.internal.has
import com.github.triplet.gradle.play.internal.nullOrFull
import com.github.triplet.gradle.play.internal.orNull
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.androidpublisher.AndroidPublisher
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import java.io.File

abstract class PlayPublishTaskBase(
        @get:Nested internal open val extension: PlayPublisherExtension,
        @get:Internal internal val variant: ApplicationVariant
) : DefaultTask() {
    internal val savedEditId = File(project.rootProject.buildDir, EDIT_ID_FILE)
    @get:Internal protected val hasSavedEdit get() = savedEditId.exists()

    @get:Internal
    protected val publisher by lazy { extension.toSerializable().buildPublisher() }

    protected fun read(
            skipIfNotFound: Boolean = false,
            block: AndroidPublisher.Edits.(editId: String) -> Unit
    ) {
        val edits = publisher.edits()
        val id = try {
            savedEditId.orNull()?.readText().nullOrFull()
                    ?: edits.insert(variant.applicationId, null).execute().id
        } catch (e: GoogleJsonResponseException) {
            if (e has "applicationNotFound") {
                if (skipIfNotFound) {
                    return
                } else {
                    // Rethrow for clarity
                    throw IllegalArgumentException(
                            "No application found for the package name ${variant.applicationId}. " +
                                    "The first version of your app must be uploaded via the " +
                                    "Play Store console.", e)
                }
            } else if (e has "editAlreadyCommitted") {
                logger.info("Failed to retrieve saved edit.")
                project.delete(savedEditId)

                return read(skipIfNotFound, block)
            } else if (e.statusCode == 401) {
                throw IllegalArgumentException(
                        "Service account not authenticated. See the README for instructions: " +
                                "https://github.com/Triple-T/gradle-play-publisher/" +
                                "blob/master/README.md#service-account", e)
            } else {
                throw e
            }
        }

        edits.block(id)
    }
}
