package com.github.triplet.gradle.play.tasks

import com.android.build.VariantOutput.OutputType
import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.api.ApplicationVariant
import com.github.triplet.gradle.play.PlayPublisherExtension
import com.github.triplet.gradle.play.internal.playPath
import com.github.triplet.gradle.play.internal.trackUploadProgress
import com.github.triplet.gradle.play.tasks.internal.PlayPublishPackageBase
import com.github.triplet.gradle.play.tasks.internal.PublishableArtifactExtensionOptions
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.Apk
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import javax.inject.Inject

open class PublishApk @Inject constructor(
        @get:Nested override val extension: PlayPublisherExtension,
        variant: ApplicationVariant
) : PlayPublishPackageBase(extension, variant), PublishableArtifactExtensionOptions {
    @Suppress("MemberVisibilityCanBePrivate", "unused") // Used by Gradle
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    protected val inputApks by lazy {
        val customDir = extension._artifactDir

        if (customDir == null) {
            variant.outputs.filterIsInstance<ApkVariantOutput>().filter {
                OutputType.valueOf(it.outputType) == OutputType.MAIN || it.filters.isNotEmpty()
            }.map { it.outputFile }
        } else {
            customDir.listFiles().orEmpty().filter { it.extension == "apk" }.ifEmpty {
                error("No APKs found in '$customDir'.")
            }
        }
    }
    @Suppress("MemberVisibilityCanBePrivate", "unused") // Used by Gradle
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:OutputDirectory
    protected val outputDir by lazy { File(project.buildDir, "${variant.playPath}/apks") }

    @TaskAction
    fun publishApks(inputs: IncrementalTaskInputs) = write { editId: String ->
        progressLogger.start("Uploads APK files for variant ${variant.name}", null)

        if (!inputs.isIncremental) project.delete(outputs.files)

        val publishedApks = mutableListOf<Apk>()
        inputs.outOfDate {
            if (inputApks.contains(file)) {
                project.copy { from(file).into(outputDir) }
                publishApk(editId, FileContent(MIME_TYPE_APK, file))?.let { publishedApks += it }
            }
        }
        inputs.removed { project.delete(File(outputDir, file.name)) }

        if (publishedApks.isNotEmpty()) {
            updateTracks(editId, publishedApks.map { it.versionCode.toLong() })
        }

        progressLogger.completed()
    }

    private fun AndroidPublisher.Edits.publishApk(editId: String, content: FileContent): Apk? {
        val apk = try {
            apks().upload(variant.applicationId, editId, content)
                    .trackUploadProgress(progressLogger, "APK")
                    .execute()
        } catch (e: GoogleJsonResponseException) {
            return handleUploadFailures(e, content.file)
        }

        handlePackageDetails(editId, apk.versionCode)

        return apk
    }

    private companion object {
        const val MIME_TYPE_APK = "application/vnd.android.package-archive"
    }
}
