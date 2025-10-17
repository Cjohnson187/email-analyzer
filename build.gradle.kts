plugins {
    // Apply the Java plugin to add support for Java.
    id("java")
    // Apply the Spring Boot plugin
    id("org.springframework.boot") version ("3.2.0")
    // Apply the Dependency Management plugin
    id ("io.spring.dependency-management") version ("1.1.4")
}

group = "org.tools"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot dependencies for a console application
    implementation ("org.springframework.boot:spring-boot-starter")

    // Jakarta Mail Implementation: Needed for the InternetAddress class used in the EmailSorter code.
    // Using the official implementation to ensure runtime classes are available.
    implementation("com.sun.mail:jakarta.mail:2.1.3")

    // Mime4j for robust MBOX and message parsing (aligned to 0.8.10 for consistency)
    // These replace the need for the full JavaMail Session.
    implementation("org.apache.james:apache-mime4j-dom:0.8.10")
    implementation("org.apache.james:apache-mime4j-core:0.8.10")
    implementation("org.apache.james:apache-mime4j-mbox-iterator:0.8.10")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Testing dependencies (standard Spring Boot setup)
    testImplementation ("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
tasks.register<JavaExec>("emailanalyzer") {
    group = "application"
    description = "Runs the main Java application"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.tools.EmailSorter") // Set to the correct main class
    // If your application requires arguments, uncomment and configure:
    // args("arg1", "arg2")
    // If your application requires JVM arguments, uncomment and configure:
    // jvmArgs("-Xmx512m")
}
