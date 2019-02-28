package com.github.triplet.gradle.play.internal

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.services.androidpublisher.AndroidPublisherRequest
import org.gradle.internal.logging.progress.ProgressLogger
import kotlin.math.roundToInt

internal fun <T> AndroidPublisherRequest<T>.retryableExecute(times: Int = 3): T {
    require(times > 0) { "The number of retries must be greater than 0." }

    for (i in times - 1 downTo 0) {
        try {
            return execute()
        } catch (e: GoogleJsonResponseException) {
            if (i == 0) throw e // We tried, throw whatever we got

            // See https://github.com/Triple-T/gradle-play-publisher/issues/504
            if (e.statusCode == 500) continue else throw e
        }
    }

    error("Impossible condition")
}

internal fun <T> AndroidPublisherRequest<T>.trackUploadProgress(
        logger: ProgressLogger,
        thing: String
): AndroidPublisherRequest<T> {
    val uploader = mediaHttpUploader ?: return this
    uploader.chunkSize = 4 * MediaHttpUploader.MINIMUM_CHUNK_SIZE
    uploader.setProgressListener {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (it.uploadState) {
            MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS ->
                logger.progress("Uploading $thing: ${(it.progress * 100).roundToInt()}% complete")
            MediaHttpUploader.UploadState.MEDIA_COMPLETE ->
                logger.progress("${thing.capitalize()} upload complete")
        }
    }
    return this
}
