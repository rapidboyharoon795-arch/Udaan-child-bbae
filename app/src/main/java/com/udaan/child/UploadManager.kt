import android.util.Log
import kotlinx.coroutines.CancellationException
import java.io.File

private const val TAG = "UploadManager"

suspend fun sendData(
    file: File,
    phone: String,
    callType: String,
    duration: Long,
    time: Long
): Boolean {
    // 1. File exist check
    if (!file.exists() || file.length() == 0L) {
        Log.e(TAG, "File does not exist or is empty: ${file.absolutePath}")
        return false
    }

    return try {
        // 2. Request body preparation
        val deviceIdBody = NetworkHelper.createPartFromString("udaan_child_device")
        val phoneBody = NetworkHelper.createPartFromString(phone)
        val typeBody = NetworkHelper.createPartFromString(callType)
        val durationBody = NetworkHelper.createPartFromString(duration.toString())
        val timeBody = NetworkHelper.createPartFromString(time.toString())
        val filePart = NetworkHelper.prepareFilePart("recording", file)

        // 3. API Call
        val response = NetworkHelper.api.uploadRecording(
            deviceId = deviceIdBody,
            phoneNumber = phoneBody,
            callType = typeBody,
            duration = durationBody,
            timestamp = timeBody,
            recording = filePart
        )

        if (response.isSuccessful) {
            Log.d(TAG, "Upload successful: ${file.name}")
            file.delete() // Local storage clean up
            true
        } else {
            Log.e(TAG, "Upload failed with code: ${response.code()} - ${response.message()}")
            false
        }
    } catch (e: CancellationException) {
        // Coroutine cancellation ko swallow nahi karna chahiye
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "sendData error during upload", e)
        false
    }
}
