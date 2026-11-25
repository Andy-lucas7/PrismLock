# ML Kit (subject segmentation) wires its components by reflection: registrars named in the
# merged manifest, internal loggers/options created through LazyInstanceMap. R8 full mode
# strips/merges those internals and the first InputImage.fromBitmap() NPEs.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
