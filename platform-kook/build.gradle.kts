plugins { `java-library` }
group = rootProject.group
version = rootProject.version
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
repositories { mavenCentral() }
dependencies {
    compileOnly(project(":"))
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    testImplementation(project(":"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.compilerArgs.add("--enable-preview") }
tasks.withType<Test> { useJUnitPlatform(); jvmArgs("--enable-preview") }
