# R8 rules for BrickTimer. Libraries (Compose, AdMob, UMP, Billing, Coil) ship their own rules.

# Enum names are stored in bricktimer.json and the Supabase "status" column (BuildStatus, ThemeMode,
# AppLanguage, ...), and read back with valueOf(): keep them exactly as written.
-keepclassmembers enum nl.nextbuzz.bricktimer.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Readable line numbers in Play crash reports; Play deobfuscates with the mapping file in the bundle.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
