/*
 * Copyright 2018 the original author or authors.
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
package gradlebuild

import accessors.base
import org.gradle.gradlebuild.versioning.buildVersion
import java.time.Year

plugins {
    `maven-publish`
}

val localRepository = layout.buildDirectory.dir("repo")

publishing {
    publications {
        create<MavenPublication>("gradleDistribution") {
            from(components["java"])
            artifactId = base.archivesBaseName
        }
    }
    repositories {
        maven {
            name = "remote"
            val libsType = if (rootProject.buildVersion.isSnapshot) "snapshots" else "releases"
            url = uri("https://repo.gradle.org/gradle/libs-$libsType-local")
            credentials {
                username = artifactoryUserName
                password = artifactoryUserPassword
            }
        }
        maven {
            name = "local"
            url = uri(localRepository)
        }
    }
    configurePublishingTasks()
}

fun Project.configurePublishingTasks() {
    tasks.named("publishGradleDistributionPublicationToRemoteRepository") {
        onlyIf { !project.hasProperty("noUpload") }
        failEarlyIfCredentialsAreNotSet(this)
    }
    val localPublish = tasks.named("publishGradleDistributionPublicationToLocalRepository") {
        doFirst {
            val moduleBaseDir = localRepository.get().dir("org/gradle/${base.archivesBaseName}").asFile
            if (moduleBaseDir.exists()) {
                // Make sure artifacts do not pile up locally
                moduleBaseDir.deleteRecursively()
            }
        }

        doLast {
            val mavenMetadataXml = localRepository.get().file("org/gradle/${base.archivesBaseName}/maven-metadata.xml").asFile
//            "size": 4523517,
//            "sha512": "4748d3d1ad52021b14b41f308dab461684d5e281a28f393f1dd171e8ea4678ac71e87ada205d552630e8ac59185d2a68848f2b279ad0acd8d08941efd0171b63",
//            "sha256": "bef23d15246d347f45857ccb5cb258510f33065433b42b46c6705ef957c7c576",
//            "sha1": "7615d66924c610d4fa49bb31973489118308f1a0",
//            "md5": "966c70fc54674d6c1043c534b3889622"
            mavenMetadataXml.writeText(
                mavenMetadataXml.readText().replace("\\Q<lastUpdated>\\E\\d+\\Q</lastUpdated>\\E".toRegex(), "<lastUpdated>${Year.now().value}0101000000</lastUpdated>")
            )

            rootProject.fileTree("build/repo/org/gradle/${base.archivesBaseName}").matching {
                include("**/*.module")
            }.forEach {
                val content = it.readText()
                    .replace("\"size\":\\s+\\d+".toRegex(), "\"size\": 0")
                    .replace("\"sha512\":\\s+\"\\w+\"".toRegex(), "\"sha512\": \"\"")
                    .replace("\"sha1\":\\s+\"\\w+\"".toRegex(), "\"sha1\": \"\"")
                    .replace("\"sha256\":\\s+\"\\w+\"".toRegex(), "\"sha256\": \"\"")
                    .replace("\"md5\":\\s+\"\\w+\"".toRegex(), "\"md5\": \"\"")
                it.writeText(content)
            }
        }
    }

    // For local consumption by tests
    configurations.create("localLibsRepositoryElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("gradle-libs-repository"))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EMBEDDED))
        }
        isCanBeResolved = false
        isCanBeConsumed = true
        isVisible = false
        outgoing.artifact(mapOf(
            "file" to localRepository.get().asFile, // This is not lazy
            "builtBy" to localPublish
        ))
    }
}

fun Project.failEarlyIfCredentialsAreNotSet(publish: Task) {
    gradle.taskGraph.whenReady {
        if (hasTask(publish)) {
            if (artifactoryUserName.isNullOrEmpty()) {
                throw GradleException("artifactoryUserName is not set!")
            }
            if (artifactoryUserPassword.isNullOrEmpty()) {
                throw GradleException("artifactoryUserPassword is not set!")
            }
        }
    }
}

val Project.artifactoryUserName
    get() = findProperty("artifactoryUserName") as String?

val Project.artifactoryUserPassword
    get() = findProperty("artifactoryUserPassword") as String?


