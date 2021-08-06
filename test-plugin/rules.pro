# The Gradle API jar isn't added to the classpath, ignore the missing symbols
-ignorewarnings

# Keep kotlin metadata so that the Kotlin compiler knows about top level functions
-keep class kotlin.Metadata { *; }
# Keep Unit as it's being used from KotlinPoet:
# https://github.com/square/kotlinpoet/blob/5395a8e2e0ef0f9e7d0ae044b66c5ef3e06db925/kotlinpoet/src/main/java/com/squareup/kotlinpoet/TypeName.kt#L223
#-keep class kotlin.Unit { *; }

# We need to keep type arguments (Signature) for Gradle to be able to instantiate abstract models like `Property`
# Else it fails with
# 'Declaration of property alwaysGenerateTypesMatching does not include any type arguments in its property type interface org.gradle.api.provider.SetProperty'
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,PermittedSubclasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable

# kotlin-reflect uses EnumSetOf that makes a reflexive access to "values"
# https://github.com/JetBrains/kotlin/blob/0f9a413ee986f4fd80e26aed2685a1823b2b4279/core/descriptors/src/org/jetbrains/kotlin/builtins/PrimitiveType.java#L39
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
}

# Keep everything Apollo it's being used from KotlinPoet and also because it shouldn't conflict
-keep class com.gradleup.** { *; }

# Allow to make some classes public so that we can repackage them without breaking package-private members
-allowaccessmodification
# Makes it easier to debug on MacOS case-insensitive filesystem when unzipping the jars
-dontusemixedcaseclassnames
-repackageclasses com.gradleup.relocated


