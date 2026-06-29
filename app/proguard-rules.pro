# ReelGuard ProGuard Rules

# Keep accessibility service
-keep class com.reelguard.app.service.** { *; }

# Keep Room entities
-keep class com.reelguard.app.data.entity.** { *; }

# Keep managers
-keep class com.reelguard.app.manager.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# JSON parsing
-keepattributes Signature
-keepattributes *Annotation*
