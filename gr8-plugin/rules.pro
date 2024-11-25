# Keep our public API
-keep class com.gradleup.** { *; }

# Keep class names to make debugging easier
-dontobfuscate

# Makes it easier to debug on MacOS case-insensitive filesystem when unzipping the jars
-repackageclasses com.gradleup.gr8.relocated

# We need to keep type arguments (Signature) for Gradle to be able to instantiate abstract models like `Property`
# Else it fails with
# 'Declaration of property alwaysGenerateTypesMatching does not include any type arguments in its property type interface org.gradle.api.provider.SetProperty'
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,PermittedSubclasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable

# kotlin-reflect uses EnumSetOf that makes a reflexive access to "values"
# https://github.com/JetBrains/kotlin/blob/0f9a413ee986f4fd80e26aed2685a1823b2b4279/core/descriptors/src/org/jetbrains/kotlin/builtins/PrimitiveType.java#L39
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
}

#-keep class com.android.tools.r8.threading.providers.blocking.ThreadingModuleBlockingProvider { *; }
#-keep class com.android.tools.r8.threading.providers.singlethreaded.ThreadingModuleSingleThreadedProvider { *; }
