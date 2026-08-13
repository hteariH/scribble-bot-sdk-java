description = "Framework-free Java client for the scribble.pub Bot API: payload types, HMAC signature verification, hook dispatch and webhook registration"

dependencies {
    // Jackson 3 (`tools.jackson`), matching Spring Boot 4. `api` because HookRequest/Action are
    // Jackson-annotated and callers may want to (de)serialise them with their own mapper.
    api(platform("tools.jackson:jackson-bom:3.0.4"))
    api("tools.jackson.core:jackson-databind")

    implementation("org.slf4j:slf4j-api:2.0.17")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
