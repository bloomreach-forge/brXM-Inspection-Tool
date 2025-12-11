dependencies {
    // Parsing libraries
    implementation("com.github.javaparser:javaparser-core:3.25.5")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

    // XML parsing
    implementation("org.dom4j:dom4j:2.1.4")

    // Reporting
    implementation("org.freemarker:freemarker:2.3.32")

    // Utilities
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Testing
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation(kotlin("test"))
}
