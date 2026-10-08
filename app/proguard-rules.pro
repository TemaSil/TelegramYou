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
