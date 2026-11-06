package com.template.framework.util

import android.content.Context
import android.os.Environment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

fun Context.externalPicturesDirectory(relativePath: String = ""): File? {
    val base = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
    val directory = if (relativePath.isBlank()) base else File(base, relativePath)
    val canonicalBase = base.canonicalFile
    val canonicalDirectory = directory.canonicalFile
    require(
        canonicalDirectory == canonicalBase || canonicalDirectory.toPath().startsWith(canonicalBase.toPath())
    ) { "relativePath must stay inside the app pictures directory" }
    return canonicalDirectory.takeIf { it.ensureDirectory() }
}

fun File.ensureDirectory(): Boolean = isDirectory || (!exists() && mkdirs())

fun File.asMultipartPart(
    formFieldName: String,
    contentType: String = "application/octet-stream"
): MultipartBody.Part {
    require(isFile) { "File does not exist or is not a regular file: $path" }
    return MultipartBody.Part.createFormData(
        formFieldName,
        name,
        asRequestBody(contentType.toMediaType())
    )
}
