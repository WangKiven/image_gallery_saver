package com.example.imagegallerysaver

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import android.text.TextUtils
import android.webkit.MimeTypeMap


class ImageGallerySaverPlugin : FlutterPlugin, MethodCallHandler {
    private var applicationContext: Context? = null
    private var methodChannel: MethodChannel? = null


    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val instance = ImageGallerySaverPlugin()
            instance.onAttachedToEngine(registrar.context(), registrar.messenger())
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result): Unit {
        when {
            call.method == "saveImageToGallery" -> {
                val image = call.argument<ByteArray>("imageBytes") ?: return
                val quality = call.argument<Int>("quality") ?: return
                val name = call.argument<String>("name")
                val dirName = call.argument<String>("dirName")

                result.success(saveImageToGallery(BitmapFactory.decodeByteArray(image, 0, image.size), quality, name, dirName))
            }
            call.method == "saveFileToGallery" -> {
                val path = call.argument<String>("file") ?: return
                val name = call.argument<String>("name")
                val dirName = call.argument<String>("dirName")
                result.success(saveFileToGallery(path, name, dirName))
            }
            else -> result.notImplemented()
        }

    }


    private fun generateUri(extension: String = "", name: String? = null, dirName: String?): Uri {
        var fileName = name ?: System.currentTimeMillis().toString()
        val addDir = if (dirName.isNullOrBlank()) "" else File.separator + dirName
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + addDir)
            val mimeType = getMIMEType(extension)
            if (!TextUtils.isEmpty(mimeType)) {
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (mimeType!!.startsWith("video")) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + addDir)
                }
            }
            return applicationContext?.contentResolver?.insert(uri, values)!!
        } else {
            val storePath = Environment.getExternalStorageDirectory().absolutePath + File.separator + Environment.DIRECTORY_PICTURES + addDir
            val appDir = File(storePath)
            if (!appDir.exists()) {
                appDir.mkdir()
            }
            if (extension.isNotEmpty()) {
                fileName += (".$extension")
            }
            return Uri.fromFile(File(appDir, fileName))
        }
    }

    private fun getMIMEType(extension: String): String? {
        var type: String? = null;
        if (!TextUtils.isEmpty(extension)) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase())
        }
        return type
    }

    private fun saveImageToGallery(bmp: Bitmap, quality: Int, name: String?, dirName: String?): HashMap<String, Any?> {
        val context = applicationContext
        val fileUri = generateUri("jpg", name, dirName)
        return try {
            val fos = context?.contentResolver?.openOutputStream(fileUri)!!
            println("ImageGallerySaverPlugin $quality")
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
            fos.close()
            context!!.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, fileUri))
            bmp.recycle()
            SaveResultModel(fileUri.toString().isNotEmpty(), fileUri.toString(), null).toHashMap()
        } catch (e: IOException) {
            SaveResultModel(false, null, e.toString()).toHashMap()
        }
    }

    private fun saveFileToGallery(filePath: String, name: String?, dirName: String?): HashMap<String, Any?> {
        val context = applicationContext
        return try {
            val originalFile = File(filePath)
            val fileUri = generateUri(originalFile.extension, name, dirName)

            val outputStream = context?.contentResolver?.openOutputStream(fileUri)!!
            val fileInputStream = FileInputStream(originalFile)

            val buffer = ByteArray(10240)
            var count = 0
            while (fileInputStream.read(buffer).also { count = it } > 0) {
                outputStream.write(buffer, 0, count)
            }

            outputStream.flush()
            outputStream.close()
            fileInputStream.close()

            context!!.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, fileUri))
            SaveResultModel(fileUri.toString().isNotEmpty(), fileUri.toString(), null).toHashMap()
        } catch (e: IOException) {
            SaveResultModel(false, null, e.toString()).toHashMap()
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(binding.applicationContext, binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = null
        methodChannel!!.setMethodCallHandler(null);
        methodChannel = null;
    }

    private fun onAttachedToEngine(applicationContext: Context, messenger: BinaryMessenger) {
        this.applicationContext = applicationContext
        methodChannel = MethodChannel(messenger, "image_gallery_saver")
        methodChannel!!.setMethodCallHandler(this)
    }

}

class SaveResultModel(var isSuccess: Boolean,
                      var filePath: String? = null,
                      var errorMessage: String? = null) {
    fun toHashMap(): HashMap<String, Any?> {
        val hashMap = HashMap<String, Any?>()
        hashMap["isSuccess"] = isSuccess
        hashMap["filePath"] = filePath
        hashMap["errorMessage"] = errorMessage
        return hashMap
    }
}
