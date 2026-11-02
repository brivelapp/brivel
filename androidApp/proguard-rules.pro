# kotlinx.serialization y Ktor usan reflexión limitada: conservar sus metadatos
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class app.brivel.**$$serializer { *; }
-keepclassmembers class app.brivel.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
