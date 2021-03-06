package com.github.triplet.gradle.play.tasks

import com.android.build.gradle.api.ApplicationVariant
import com.github.triplet.gradle.play.PlayPublisherExtension
import com.github.triplet.gradle.play.internal.PRODUCTS_PATH
import com.github.triplet.gradle.play.internal.isDirectChildOf
import com.github.triplet.gradle.play.internal.playPath
import com.github.triplet.gradle.play.tasks.internal.PlayPublishTaskBase
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.androidpublisher.model.InAppProduct
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.io.File
import javax.inject.Inject

open class PublishProducts @Inject constructor(
        extension: PlayPublisherExtension,
        variant: ApplicationVariant
) : PlayPublishTaskBase(extension, variant) {
    @get:Internal
    internal lateinit var resDir: File
    @Suppress("MemberVisibilityCanBePrivate", "unused") // Used by Gradle
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    protected val productsDir by lazy { File(resDir, PRODUCTS_PATH) }

    @Suppress("MemberVisibilityCanBePrivate") // Needed for Gradle caching to work correctly
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:OutputFile
    val outputFile by lazy { File(project.buildDir, "${variant.playPath}/products-cache-key") }

    @TaskAction
    fun publishListing(inputs: IncrementalTaskInputs) {
        if (!inputs.isIncremental) project.delete(outputs.files)

        val changedProducts = mutableSetOf<File>()

        fun File.process() {
            if (invalidatesProduct()) changedProducts += this
        }

        inputs.outOfDate { file.process() }
        inputs.removed { file.process() }

        progressLogger.start("Uploads in-app products for variant ${variant.name}", null)
        publisher.inappproducts().apply {
            changedProducts.map {
                JacksonFactory.getDefaultInstance()
                        .createJsonParser(it.inputStream())
                        .parse(InAppProduct::class.java)
            }.forEach {
                progressLogger.progress("Uploading ${it.sku}")
                update(variant.applicationId, it.sku, it).execute()
            }

            outputFile.writeText(hashCode().toString())
        }
        progressLogger.completed()
    }

    private fun File.invalidatesProduct() = isDirectChildOf(PRODUCTS_PATH)
}
