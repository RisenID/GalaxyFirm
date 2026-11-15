buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.google.devtools.ksp") version "2.3.14" apply false
}
