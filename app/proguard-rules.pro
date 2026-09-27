# TDLib's JSON bridge: JsonClient is called from native code by name and
# declares native methods, which R8 cannot see being used.
-keep class org.drinkless.tdlib.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
