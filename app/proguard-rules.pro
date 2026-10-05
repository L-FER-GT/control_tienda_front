# Modelos serializables con kotlinx.serialization (rutas de navegación y API de Open Food Facts)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.lfergt.controltienda.**$$serializer { *; }
-keepclassmembers class com.lfergt.controltienda.** {
    *** Companion;
}
-keepclasseswithmembers class com.lfergt.controltienda.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ML Kit (escáner): ComponentDiscovery crea los registrars por reflexión con su constructor vacío.
# La regla de la librería conserva la clase pero no el constructor, y R8 en modo completo lo borra:
# sin registrars, BarcodeScanning.getClient() lanza NullPointerException en el build release
# (la v1.0.0 se cerraba al abrir el escáner).
-keep class * implements com.google.firebase.components.ComponentRegistrar { void <init>(); }
