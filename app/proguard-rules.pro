# تجاهل تحذيرات المكتبات الاختيارية التي يطلبها LittleProxy و Netty
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn com.jcraft.jzlib.**
-dontwarn javassist.**
-dontwarn javax.annotation.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.tomcat.jni.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn sun.security.**

# الحفاظ على كلاسات LittleProxy و Netty الأساسية لمنع R8 من كسرها أثناء التشفير
-keep class org.littleshoot.proxy.** { *; }
-keep class io.netty.** { *; }

# الحفاظ على كلاسات Shizuku
-keep class rikka.shizuku.** { *; }
