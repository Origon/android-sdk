# Keep the SDK's JNI bridge entry points — they're called from native code
# by name, so R8 must not rename or strip them.
-keep class ai.origon.sdk.SessionBridge { *; }
