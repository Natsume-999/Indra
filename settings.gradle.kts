rootProject.name = "Indra"

// ============================================================================
//  插件仓库
//  ⚠️ 默认的 Gradle Plugin Portal（plugins.gradle.org）在国内部分网络不可达，
//     会导致 "Plugin [id: 'org.jetbrains.kotlin.jvm'] was not found"。
//     这里把阿里云 / 腾讯镜像排在最前，官方源作为兜底。
// ============================================================================
pluginManagement {
    repositories {
        // Kotlin Gradle 插件在阿里云有专门镜像，优先
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        // TabooLib Gradle 插件
        maven("https://repo.tabooproject.org/repository/releases/")
        // 官方源兜底
        gradlePluginPortal()
        mavenCentral()
    }
}
