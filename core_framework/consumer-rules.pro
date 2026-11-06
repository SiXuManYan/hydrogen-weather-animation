# Gson 反射读取示例响应字段，其他 HTTP 类交给 R8 正常优化。
-keep class com.template.framework.api.model.ApiResponse { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
