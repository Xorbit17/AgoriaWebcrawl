import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    application
}

group = "me.dennis"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    //  compile group: 'org.seleniumhq.selenium', name: 'selenium-java', version: '4.0.0' // Groovy from selenium site
    implementation(group = "org.seleniumhq.selenium", name = "selenium-java", version = "4.0.0")
    implementation("io.github.bonigarcia:webdrivermanager:5.0.3")
    /*
            <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi</artifactId>
            <version>4.1.2</version>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>4.1.2</version>
        </dependency>
     */
    implementation(group="org.apache.poi", name="poi", version="4.1.2")
    implementation(group="org.apache.poi", name="poi-ooxml", version="4.1.2")

}


tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}