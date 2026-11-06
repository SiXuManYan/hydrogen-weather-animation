package com.template.framework.api.model

import com.google.gson.annotations.SerializedName

/**
 * Generic envelope for the sample backend response format.
 *
 * - 中文：示例后端的统一响应结构，不要求所有业务采用相同协议。
 *
 * ## JSON shape
 * ```json
 * {
 *   "code": 200,
 *   "msg": "成功",
 *   "data": { ... } | [ ... ]
 * }
 * ```
 *
 * ## Usage
 * ```kotlin
 * @GET("user/info")
 * suspend fun getUserInfo(): ApiResponse<UserInfo>
 * ```
 *
 * @property code backend business status code
 * @property message human-readable backend message mapped from JSON field `msg`
 * @property data nullable response payload
 */
data class ApiResponse<T>(
    val code: Int,
    @SerializedName("msg")
    val message: String,
    val data: T?
) {
    /** `true` when [code] equals [SUCCESS_CODE]. */
    val isSuccess: Boolean
        get() = code == SUCCESS_CODE

    companion object {
        /** Business code used by the sample backend for success. */
        const val SUCCESS_CODE = 200

        /** Business-level code used by the sample backend for an expired token. */
        const val TOKEN_EXPIRED_CODE = 401
    }
}
