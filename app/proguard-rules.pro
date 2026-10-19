# Afterglow has no reflection-based libraries; the default optimize rules are enough.

# dadb (adb client for the TV's own debugging port): keep its protocol classes, okio ships its own rules.
-keep class dadb.** { *; }
-dontwarn dadb.adbserver.**
-dontwarn org.slf4j.**
