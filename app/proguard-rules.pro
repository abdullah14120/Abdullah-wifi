# =========================================================
# 1. تجاهل تحذيرات المكتبات الاختيارية (Optional Dependencies)
# =========================================================

-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn com.jcraft.jzlib.**
-dontwarn javassist.**
-dontwarn javax.annotation.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.tomcat.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn sun.security.**
-dontwarn com.google.protobuf.**
-dontwarn com.sun.nio.sctp.**
-dontwarn gnu.io.**
-dontwarn org.apache.commons.cli.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.jboss.marshalling.**
-dontwarn org.littleshoot.dnssec4j.**

# =========================================================
# 2. الحفاظ على كلاسات التطبيق الأساسية (Keep Rules)
# =========================================================

# الحفاظ على كلاسات LittleProxy و Netty الأساسية لمنع كسرها
-keep class org.littleshoot.proxy.** { *; }
-keep class io.netty.** { *; }

# الحفاظ على كلاسات Shizuku
-keep class rikka.shizuku.** { *; }
