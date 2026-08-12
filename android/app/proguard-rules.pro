# Compile-only annotations referenced by Tink through security-crypto.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# JNI entry points and tensor wrappers used by the local classic NLU model.
-keep class ai.onnxruntime.** { *; }
