# TDLib's JSON bridge: JsonClient is called from native code by name and
# declares native methods, which R8 cannot see being used.
-keep class org.drinkless.tdlib.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Home-screen widgets (2.1): Glance creates a button's ActionCallback by
# its class name when the button is pressed, so its class and no-argument
# constructor must survive shrinking.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager, which Glance brings in for the widgets (2.1), keeps its jobs
# in a Room database that Room creates by name — WorkDatabase_Impl, by
# reflection, from androidx.startup as the app starts. R8 renamed it away in
# 2.1's release, and the app died before its first screen, on every phone,
# while the debug builds every test runs were fine. The Release check
# workflow now starts the release on an emulator so it cannot happen quietly.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
