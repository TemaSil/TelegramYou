# Only with -PtestRelease=true, which the Release check uses to run
# instrumentation tests against the shrunk release; never in a published
# build. The test APK leaves out any library the app already contains and
# calls the app's copy — which R8 has renamed or removed wherever the app
# itself did not need it. AndroidJUnitRunner traces through androidx.tracing,
# which is such a library.
-keep class androidx.tracing.** { *; }
