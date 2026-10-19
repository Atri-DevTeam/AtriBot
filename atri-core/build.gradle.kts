plugins {
    `java-library`
    `maven-publish`
    id("com.vanniktech.maven.publish.base") version "0.37.0"
}

group = "top.yzljc.atricore"
version = providers.gradleProperty("coreVersion").getOrElse("0.1.0-SNAPSHOT")
description = "Atri QQ and Napcat framework"

repositories {
    mavenCentral()
}

dependencies {
    api("io.javalin:javalin:6.6.0")
    api("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
    api("com.google.guava:guava:33.7.2-jre")
    api("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    api("com.fasterxml.jackson.core:jackson-annotations:2.22")
    api("org.yaml:snakeyaml:2.7")
    api("org.java-websocket:Java-WebSocket:1.6.0")
    api("org.slf4j:slf4j-api:2.0.20")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val runtimeDirectory = layout.buildDirectory.dir("test-runtime").get().asFile
    workingDir(runtimeDirectory)
    doFirst { runtimeDirectory.mkdirs() }
    jvmArgs("--enable-preview")
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        artifactId = "atri-core"
        pom {
            name.set("atri-core")
            description.set(project.description)
            url.set("https://github.com/Atri-DevTeam/AtriBot/tree/AtriMeow/atri-core")
            inceptionYear.set("2026")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/license/mit")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("YZ_Ljc_")
                    name.set("YZ_Ljc_")
                    email.set("contact@yzljc.top")
                }
            }
            scm {
                url.set("https://github.com/Atri-DevTeam/AtriBot")
                connection.set("scm:git:https://github.com/Atri-DevTeam/AtriBot.git")
                developerConnection.set("scm:git:ssh://git@github.com/Atri-DevTeam/AtriBot.git")
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}

val delombok by tasks.registering(JavaExec::class) {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    classpath = configurations.annotationProcessor.get()
    mainClass.set("lombok.launch.Main")
    val outputDirectory = layout.buildDirectory.dir("generated/sources/delombok")
    inputs.files(sourceSets.main.get().allJava)
    inputs.files(configurations.compileClasspath)
    outputs.dir(outputDirectory)
    jvmArgs("--enable-preview")
    args("delombok", "src/main/java", "-d", outputDirectory.get().asFile.absolutePath,
        "--classpath", configurations.compileClasspath.get().asPath, "--encoding", "UTF-8")
}

tasks.withType<Javadoc>().configureEach {
    dependsOn(delombok)
    setSource(layout.buildDirectory.dir("generated/sources/delombok"))
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        charSet = "UTF-8"
        addBooleanOption("-enable-preview", true)
        source = "25"
        addBooleanOption("Xdoclint:all,-missing", true)
        tags("Author:a:Author", "ClassName:t:Class", "Created_at:a:Created", "Project:a:Project",
            "Package:a:Package", "Description:a:Description")
    }
}
