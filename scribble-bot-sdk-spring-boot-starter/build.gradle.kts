description = "Spring Boot starter for the scribble.pub Bot API: auto-configured webhook endpoint, scribble.* properties and token resolution"

dependencies {
    api(project(":scribble-bot-sdk"))

    // Constrains our own resolution; consumers' Boot BOM wins over the versions in our POM.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.3"))
    api("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:4.0.3"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.3"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
