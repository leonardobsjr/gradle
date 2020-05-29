/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.Test
import org.gradle.gradlebuild.testing.integrationtests.cleanup.DaemonTracker
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider


/**
 * Base class for all tests that check the end-to-end behavior of a Gradle distribution.
 */
abstract class DistributionTest : Test() {

    @Internal
    val binaryDistributions = BinaryDistributionsEnvironmentProvider(project)

    @Internal
    val gradleInstallationForTest = GradleInstallationForTestEnvironmentProvider(project)

    @Internal
    val libsRepository = LibsRepositoryEnvironmentProvider(project)

    @get:Internal
    abstract val tracker: Property<DaemonTracker>

    @get:Internal
    @get:Option(option = "rerun", description = "Always rerun the task")
    val rerun: Property<Boolean> = project.objects.property<Boolean>()
        .convention(
            project.providers.systemProperty("idea.active")
                .map { true }
                .orElse(project.provider { false })
        )

    @Option(option = "no-rerun", description = "Only run the task when necessary")
    fun setNoRerun(value: Boolean) {
        rerun.set(!value)
    }

    init {
        jvmArgumentProviders.add(gradleInstallationForTest)
        jvmArgumentProviders.add(binaryDistributions)
        jvmArgumentProviders.add(libsRepository)
        outputs.upToDateWhen {
            !rerun.get()
        }
    }

    override fun executeTests() {
        addTestListener(tracker.get().newDaemonListener())
        super.executeTests()
    }
}


class LibsRepositoryEnvironmentProvider(project: Project) : CommandLineArgumentProvider, Named {

    @Internal
    val dir = project.files()

    @get:Classpath
    val jars: FileCollection
        get() = dir.asFileTree.matching { include("**/*.jar") }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val metadatas: FileCollection
        get() = dir.asFileTree.matching {
                include("**/*.pom")
                include("**/*.xml")
                include("**/*.metadata")
            }

    override fun asArguments() =
        if (!dir.isEmpty) mapOf("integTest.libsRepo" to dir.singleFile).asSystemPropertyJvmArguments()
        else emptyList()

    @Internal
    override fun getName() =
        "libsRepository"
}


class GradleInstallationForTestEnvironmentProvider(project: Project) : CommandLineArgumentProvider, Named {

    @Internal
    val gradleHomeDir = project.files()

    @Internal
    val gradleUserHomeDir = project.objects.directoryProperty()

    @Internal
    val gradleSnippetsDir = project.objects.directoryProperty()

    @Internal
    val daemonRegistry = project.objects.directoryProperty()

    @get:Nested
    val gradleDistribution = GradleDistribution(gradleHomeDir)

    override fun asArguments(): Iterable<String> {
        val distributionDir = if (gradleHomeDir.files.size == 1) gradleHomeDir.singleFile else null
        val distributionName = if (distributionDir != null) {
            // complete distribution is used from 'build/bin distribution'
            distributionDir.parentFile.parentFile.name
        } else {
            // gradle-runtime-api-info.jar in 'build/libs'
            gradleHomeDir.filter { it.name.startsWith("gradle-runtime-api-info") }.singleFile.parentFile.parentFile.parentFile.name
        }
        return (
            (if (distributionDir != null) mapOf("integTest.gradleHomeDir" to distributionDir) else emptyMap()) + mapOf(
                "integTest.gradleUserHomeDir" to absolutePathOf(gradleUserHomeDir.dir(distributionName)),
                "integTest.samplesdir" to absolutePathOf(gradleSnippetsDir),
                "org.gradle.integtest.daemon.registry" to absolutePathOf(daemonRegistry)
            )
        ).asSystemPropertyJvmArguments()
    }

    @Internal
    override fun getName() =
        "gradleInstallationForTest"
}


class BinaryDistributionsEnvironmentProvider(project: Project) : CommandLineArgumentProvider, Named {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val binDistributionZip = project.files()

    override fun asArguments() =
        if (binDistributionZip.isEmpty) {
            emptyList()
        } else {
            mapOf("integTest.binDistribution" to binDistributionZip.singleFile).asSystemPropertyJvmArguments()
        }

    @Internal
    override fun getName() =
        "binaryDistributions"
}


private
fun absolutePathOf(provider: Provider<Directory>) =
    provider.get().asFile.absolutePath


internal
fun <K, V> Map<K, V>.asSystemPropertyJvmArguments(): Iterable<String> =
    map { (key, value) -> "-D$key=$value" }
