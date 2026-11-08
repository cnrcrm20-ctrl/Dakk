# Dakk ProGuard/R8 kuralları.
# Not: Şu anda release derlemelerinde küçültme (minify) kapalıdır.
# Etkinleştirilirse Firestore ve Credential Manager için aşağıdaki kurallar korunmalıdır.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keepattributes Signature, *Annotation*
