// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Definimos los plugins que se aplicarán a los módulos de tu proyecto.
    // Usamos el ID directo del plugin con su versión para asegurar la compatibilidad.
    // He actualizado las versiones a las más recientes y estables probables para Julio 2025.
    id("com.android.application") version "8.11.0" apply false // Android Gradle Plugin (AGP)
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false // Kotlin Gradle Plugin
    id("com.google.gms.google-services") version "4.4.2" apply false // Google Services para Firebase
}