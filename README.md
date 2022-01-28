# Info
Web crawler script specifiek voor Agoria. Exporteert naar Excel Script runt enkel in IDE. Geen deployment. Interne tool.

Command line arguments:
1. Output path and filename
2. Max number of pages written (script reads until pages run out of omitted)

Tech stack FYI
* Kotlin
* Gradle
* Selenium (Chrome webdriver)
* Apache POI
* Running on GraalVM (But would also work on other JDK)