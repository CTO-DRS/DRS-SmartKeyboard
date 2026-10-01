# Disable obfuscation (we use Proguard exclusively for optimization)
-dontobfuscate

# DRS v1.18.0: explicit entry-point keeps. The v1.7-era AGP-9 failure
# ("blank screen": every Compose screen, material3 and the JetPref
# runtime pruned into a 3.1MB dex) is rooted in the shrinker seeing no
# manifest-generated keep rules for our component classes, so the whole
# UI call graph hanging off them was judged unreachable and pruned.
# Keep the four manifest components explicitly.
-keep class com.drs.smartkeyboard.DrsApplication { *; }
-keep class com.drs.smartkeyboard.DrsImeService { *; }
-keep class com.drs.smartkeyboard.DrsSpellCheckerService { *; }
-keep class com.drs.smartkeyboard.app.DrsAppActivity { *; }

# DRS JetPref (vendored): generated preference model implementations are loaded
# reflectively via Class.forName(modelClass.qualifiedName + "Impl") and must be
# kept together with the models themselves. These rules mirror the consumer
# rules shipped with lib/jetpref/datastore-model (belt and suspenders).
-keep class * extends org.drs.jetpref.datastore.model.PreferenceModel { *; }
-keepclassmembers class * extends org.drs.jetpref.datastore.model.PreferenceModel {
    <init>();
}

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
