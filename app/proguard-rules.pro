# Project-specific R8 rules belong here when release integrations are added.
# MariaDB Connector/J is loaded through DriverManager at runtime.
-keep class org.mariadb.jdbc.Driver { *; }

# Connector/J includes optional desktop, Windows, AWS, GSSAPI, JMX, and SLF4J
# integrations. Linkora uses only a local TCP connection on Android.
-dontwarn com.sun.jna.**
-dontwarn java.lang.management.**
-dontwarn java.sql.SQLType
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.security.auth.login.**
-dontwarn javax.sql.XAConnection
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.**
-dontwarn software.amazon.awssdk.**
-dontwarn waffle.windows.**
