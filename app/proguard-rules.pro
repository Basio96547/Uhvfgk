# Shrink, but never rename.
#
# -dontobfuscate is not caution, it is correctness here. JNI resolves native
# methods by the *runtime* class name: every symbol in llama_bridge.cpp is
# spelled Java_com_basel_ai_llm_LlamaBridge_*, so letting R8 rename that class
# breaks every native call — from a build that compiled and linked perfectly,
# with no error until a model is loaded on a real phone.
#
# Renaming buys very little anyway. The size comes from tree-shaking, which
# still runs: material-icons-extended alone ships two thousand icons and this
# app draws forty-five of them. Keeping names also keeps stack traces and the
# class names ErrorLog reports readable, which is worth more than the bytes.
-dontobfuscate

# The native bridge, belt and braces even with renaming off.
-keepclasseswithmembernames class com.basel.ai.llm.LlamaBridge {
    native <methods>;
}
-keep class com.basel.ai.llm.LlamaBridge$TokenCallback { *; }

# MediaPipe / TensorFlow Lite native bindings must be kept.
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.**

# PDFBox reaches for classes by name and references desktop Java that Android
# does not have. Neither is a problem at runtime — the code paths are not
# taken — but both make R8 complain, and a missing class it *does* need would
# be a crash on the first PDF rather than a warning.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**
-dontwarn java.awt.**
