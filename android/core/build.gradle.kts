plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
