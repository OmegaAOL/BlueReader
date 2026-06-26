-dontobfuscate

-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontwarn org.conscrypt.**

-keepattributes LineNumberTable,SourceFile,RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod

-keepclassmembers class * extends org.omegaaol.bluereader.io.WritableObject {
	*;
}

-keepclassmembers class * extends org.omegaaol.bluereader.jsonwrap.JsonObject$JsonDeserializable {
	*;
}

-keepclassmembers class org.omegaaol.bluereader.R { *; }
-keepclassmembers class org.omegaaol.bluereader.R$xml {	*; }
-keepclassmembers class org.omegaaol.bluereader.R$string {	*; }

-keepclassmembers class com.github.luben.zstd.* {
	*;
}

-if @kotlinx.serialization.Serializable class **
{
    static **$* *;
}

-keepnames class <1>$$serializer { # -keepnames suffices; class is kept when serializer() is kept.
    static <1>$$serializer INSTANCE;
}
