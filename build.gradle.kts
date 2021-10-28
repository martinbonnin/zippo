plugins {
  id("org.jetbrains.kotlin.jvm").version("1.5.31")
  id("maven-publish")
  id("signing")
}

repositories {
  mavenCentral()
}

group = "net.mbonnin.zippo"
version = "0.1"


val emptyJavadocJarTaskProvider = tasks.register("emptyJavadocJar", org.gradle.jvm.tasks.Jar::class.java) {
  archiveClassifier.set("javadoc")
}

publishing {
  repositories {
    maven {
      name = "ossSnapshots"
      url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
      credentials {
        username = System.getenv("OSSRH_USER")
        password = System.getenv("OSSRH_PASSWORD")
      }
    }

    maven {
      name = "ossStaging"
      setUrl {
        uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
      }
      credentials {
        username = System.getenv("OSSRH_USER")
        password = System.getenv("OSSRH_PASSWORD")
      }
    }
  }


  publications.create("default", MavenPublication::class.java) {
    from(project.components.getByName("java"))
    artifact(createJavaSourcesTask())
    artifact(emptyJavadocJarTaskProvider)
    setDefaultPomFields(this)
  }
}

fun Project.createJavaSourcesTask(): TaskProvider<Jar> {
  return tasks.register("javaSourcesJar", Jar::class.java) {
    /**
     * Add a dependency on the compileKotlin task to make sure the generated sources like
     * antlr or SQLDelight get included
     * See also https://youtrack.jetbrains.com/issue/KT-47936
     */
    dependsOn("compileKotlin")

    archiveClassifier.set("sources")
    val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
    from(sourceSets.getByName("main").allSource)
  }
}


fun Project.setDefaultPomFields(mavenPublication: MavenPublication) {
  mavenPublication.groupId = group.toString()
  mavenPublication.version = version.toString()
  mavenPublication.artifactId = "zippo"

  mavenPublication.pom {
    name.set("Zippo")

    val githubUrl = "https://github.com/martinbonnin/zippo"

    description.set("A set of helpers to create zip files")
    url.set(githubUrl)

    scm {
      url.set(githubUrl)
      connection.set(githubUrl)
      developerConnection.set(githubUrl)
    }

    licenses {
      license {
        name.set("MIT License")
        url.set("https://github.com/martinbonnin/zippo/blob/main/LICENSE")
      }
    }

    developers {
      developer {
        id.set("martinbonnin")
        name.set("martinbonnin")
      }
    }
  }
}

signing {
  // GPG_PRIVATE_KEY should contain the armoured private key that starts with -----BEGIN PGP PRIVATE KEY BLOCK-----
  // It can be obtained with gpg --armour --export-secret-keys KEY_ID
  useInMemoryPgpKeys(System.getenv("GPG_KEY"), System.getenv("GPG_KEY_PASSWORD"))
  sign(publishing.publications)
}

tasks.withType(Sign::class.java).configureEach {
  isEnabled = !System.getenv("GPG_KEY").isNullOrBlank()
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

fun isTag(): Boolean {
  val ref = System.getenv("GITHUB_REF")

  return ref?.startsWith("refs/tags/") == true
}

tasks.register("ci") {
  dependsOn("build")
  if (isTag()) {
    dependsOn("publishAllPublicationsToOssStagingRepository")
    dependsOn("publishPlugins")
  }
}

