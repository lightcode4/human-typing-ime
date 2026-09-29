# Keep AndroidX biometric integration and the model types stored by the app.
-keep class androidx.biometric.** { *; }
-keep class com.example.humantypingime.Template { *; }
-keep class com.example.humantypingime.VaultStore$VaultItem { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
