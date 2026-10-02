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
