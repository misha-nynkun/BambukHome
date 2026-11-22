# Paho MQTT uses reflection in a few places.
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**
-dontwarn org.jsoup.**
