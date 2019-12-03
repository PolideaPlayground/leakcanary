package com.squareup.leakcanary.deobfuscation

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class LeakCanaryLeakDeobfuscationPluginTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var buildFile: File

  @Before
  fun setup() {
    buildFile = tempFolder.newFile("build.gradle")

    val localPropertiesFile = File("../local.properties")
    if (localPropertiesFile.exists()) {
      localPropertiesFile.copyTo(File(tempFolder.root, "local.properties"), overwrite = true)
    } else {
      System.getenv("ANDROID_HOME")?.let { androidHome->
        tempFolder.newFile("local.properties").apply {
          writeText("sdk.dir=$androidHome")
        }
      }
    }

    File("src/test/test-project").copyRecursively(tempFolder.root)
  }

  @Test
  fun `leakcanary deobfuscation plugin runs and copies mapping file into the apk assets dir`() {
    buildFile.writeText(
      """
        plugins {
          id 'com.android.application'
          id 'com.squareup.leakcanary.deobfuscation'
        }
        
        allprojects {
          repositories {
            google()
            jcenter()
          }
        }
        
        android {
          compileSdkVersion 29
  
          defaultConfig {
            minSdkVersion 29
          }
  
          buildTypes {
            debug {
              minifyEnabled true
              proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
            }
          }
        }
        
        leakCanary {
          filterObfuscatedVariants { variant ->
            variant.name == "debug"
          }
        }
      """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(tempFolder.root)
      .withArguments("clean", "assembleDebug")
      .withPluginClasspath()
      .build()

    // task has been run
    assertThat(
      result.task(":leakCanaryCopyObfuscationMappingForDebug")?.outcome == SUCCESS
    ).isTrue()

    val sb  = StringBuilder()
    sb.append("files:")
    // apk has been built
    val apkFile = File(tempFolder.root, "build/outputs/apk/debug")
      .listFiles().run {
          this.forEach {
            sb.append(it.absolutePath)
            sb.append("\n")
          }
          this
        }
      ?.firstOrNull { it.extension == "apk" }
    assertThat(apkFile != null).isTrue()

    // apk contains obfuscation mapping file in assets dir
    val obfuscationMappingEntry = ZipFile(apkFile).use { zipFile ->
      zipFile.entries().toList().forEach { entry ->
        sb.append(entry.name)
        sb.append("\n")
//        entry.name.contains("assets/leakCanaryObfuscationMapping.txt")
      }
    }
    assertThat(sb.toString()).isEqualToIgnoringCase("foo")
//    assertThat(obfuscationMappingEntry != null).isTrue()
  }

  @Test
  fun `leakcanary deobfuscation plugin doesn't copy mapping file if it hasn't been configured`() {
    buildFile.writeText(
      """
        plugins {
          id 'com.android.application'
          id 'com.squareup.leakcanary.deobfuscation'
        }
        
        allprojects {
          repositories {
            google()
            jcenter()
          }
        }
        
        android {
          compileSdkVersion 29
  
          defaultConfig {
            minSdkVersion 29
          }
  
          buildTypes {
            debug {
              minifyEnabled true
              proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
            }
          }
        }
      """.trimIndent()
    )

    GradleRunner.create()
        .withProjectDir(tempFolder.root)
        .withArguments("clean", "assembleDebug")
        .withPluginClasspath()
        .build()

    // apk has been built
    val apkFile = File(tempFolder.root, "build/outputs/apk/debug")
        .listFiles()
        ?.firstOrNull { it.extension == "apk" }
    assertThat(apkFile != null).isTrue()

    // apk doesn't contain obfuscation mapping file in assets dir
    val obfuscationMappingEntry = ZipFile(apkFile).use { zipFile ->
      zipFile.entries().toList().firstOrNull { entry ->
        entry.name.contains("assets/leakCanaryObfuscationMapping.txt")
      }
    }
    assertThat(obfuscationMappingEntry == null).isTrue()
  }

  @Test
  fun `should throw if android plugin is not applied before deobfuscation plugin`() {
    buildFile.writeText(
      """
        plugins {
          id 'com.squareup.leakcanary.deobfuscation'
        }
      """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(tempFolder.root)
      .withPluginClasspath()
      .buildAndFail()

    assertThat(
      result.output.contains(
    "LeakCanary deobfuscation plugin can be used only in Android application or library module."
      )
    ).isTrue()
  }

  @Test
  fun `should throw if there is no variant with enabled minification`() {
    buildFile.writeText(
      """
        plugins {
          id 'com.android.application'
          id 'com.squareup.leakcanary.deobfuscation'
        }
        
        allprojects {
          repositories {
            google()
            jcenter()
          }
        }
        
        android {
          compileSdkVersion 29
  
          defaultConfig {
            minSdkVersion 29
          }
  
          buildTypes {
            debug {}
          }
        }
        
        leakCanary {
          filterObfuscatedVariants { variant ->
            variant.name == "debug"
          }
        }
      """.trimIndent()
    )

    val result = GradleRunner.create()
      .withProjectDir(tempFolder.root)
      .withArguments("clean", "assembleDebug")
      .withPluginClasspath()
      .buildAndFail()

    assertThat(
      result.output.contains(
    "LeakCanary deobfuscation plugin couldn't find any variant with minification enabled."
      )
    ).isTrue()
  }
}
