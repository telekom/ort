val jacksonVersion: String by project

plugins {
    `java-library`
}

version = "latest-stable-1235-g35b79fd"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":model"))
    api(project(":reporter"))
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

}
