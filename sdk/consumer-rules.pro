# Keep all public SDK classes
-keep class ai.origon.sdk.OrigonClient { *; }
-keep class ai.origon.sdk.OrigonException { *; }

# Keep all model classes (data classes, enums, sealed classes)
-keep class ai.origon.sdk.Channel { *; }
-keep class ai.origon.sdk.Control { *; }
-keep class ai.origon.sdk.MessageRole { *; }
-keep class ai.origon.sdk.Message { *; }
-keep class ai.origon.sdk.SessionInfo { *; }
-keep class ai.origon.sdk.SessionSummary { *; }
-keep class ai.origon.sdk.StartSessionOptions { *; }
-keep class ai.origon.sdk.SendMessagePayload { *; }
-keep class ai.origon.sdk.ToolCall { *; }
-keep class ai.origon.sdk.AttachmentInfo { *; }
-keep class ai.origon.sdk.UploadProgress { *; }
-keep class ai.origon.sdk.ClientConfig { *; }
-keep class ai.origon.sdk.ClientEvent { *; }
-keep class ai.origon.sdk.ClientEvent$* { *; }

# Keep JNI bridge (internal but must not be stripped)
-keep class ai.origon.sdk.NativeBridge { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
