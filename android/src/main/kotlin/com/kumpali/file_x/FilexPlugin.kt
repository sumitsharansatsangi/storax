package com.kumpali.file_x

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import androidx.core.net.toUri

/**
 * Request code used for SAF (Storage Access Framework) folder picker.
 *
 * This must be a compile-time constant so it can be safely used
 * across Activity restarts and configuration changes.
 */
private const val SAF_REQUEST_CODE = 9091

/**
 * FilexPlugin
 *
 * This plugin provides a complete file-management backend for Flutter,
 * supporting:
 *
 * - Native file system (internal storage, SD card, USB, adopted storage)
 * - SAF (Storage Access Framework) folders
 * - Directory listing and recursive traversal
 * - File filtering (size, date, extension, MIME)
 * - Permission diagnostics
 * - USB insert/remove detection
 *
 * The plugin is ActivityAware because:
 * - SAF requires an Activity to launch system UI
 * - Permission screens require an Activity
 */
class FilexPlugin :
    FlutterPlugin,
    MethodChannel.MethodCallHandler,
    ActivityAware {

    /**
     * MethodChannel used to communicate with Flutter.
     * All file operations are triggered via this channel.
     */
    private lateinit var channel: MethodChannel

    /**
     * Application context.
     *
     * Safe to keep long-term. Used for:
     * - ContentResolver
     * - StorageManager
     * - BroadcastReceiver registration
     */
    private lateinit var context: Context

    /**
     * Reference to the current Activity.
     *
     * Required for:
     * - Opening SAF picker
     * - Opening Android settings screens
     */
    private var activity: Activity? = null

    /**
     * Single-thread executor used for all IO-heavy work.
     *
     * Prevents ANRs by ensuring filesystem access never
     * blocks the main UI thread.
     */
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ─────────────────────────────────────────────
    // Flutter engine lifecycle
    // ─────────────────────────────────────────────

    /**
     * Called when the Flutter engine attaches this plugin.
     *
     * This is where we:
     * - Save application context
     * - Create the MethodChannel
     * - Register USB insert/remove listeners
     */
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "file_x")
        channel.setMethodCallHandler(this)
        registerUsbReceiver()
    }

    /**
     * Called when the Flutter engine detaches this plugin.
     *
     * Cleanup is critical here to avoid:
     * - Memory leaks
     * - Zombie broadcast receivers
     * - Thread leaks
     */
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        unregisterUsbReceiver()
        executor.shutdown()
        channel.setMethodCallHandler(null)
    }

    // ─────────────────────────────────────────────
    // Activity lifecycle (SAF + permissions)
    // ─────────────────────────────────────────────

    /**
     * Called when the plugin is attached to an Activity.
     *
     * We:
     * - Capture the Activity reference
     * - Register for SAF picker results
     */
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener { rc, res, data ->
            if (rc == SAF_REQUEST_CODE && res == Activity.RESULT_OK) {
                data?.data?.let {
                    // Persist access so it survives app restarts
                    persistSafPermission(it)

                    // Notify Flutter that a SAF folder was picked
                    channel.invokeMethod("onSafPicked", it.toString())
                }
                true
            } else false
        }
    }

    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivity() { activity = null }

    // ─────────────────────────────────────────────
    // MethodChannel entry point (public API)
    // ─────────────────────────────────────────────

    /**
     * Receives method calls from Flutter.
     *
     * Each method corresponds to a single backend capability:
     * - Storage discovery
     * - Directory listing
     * - Traversal
     * - Permissions
     * - Diagnostics
     */
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {

            /**
             * Returns all native filesystem roots:
             * - Internal storage
             * - SD card
             * - USB
             * - Adopted storage
             */
            "getNativeRoots" ->
                result.success(getNativeRoots())

            /**
             * Returns a unified list of:
             * - Native roots
             * - SAF roots (user-picked folders)
             */
            "getAllRoots" ->
                result.success(getUnifiedRoots())

            /**
             * Lists immediate children of a directory.
             *
             * This is non-recursive and optimized for UI browsing.
             */
            "listDirectory" -> {
                val target = call.argument<String>("target") ?: return
                val isSaf = call.argument<Boolean>("isSaf") ?: false
                val filters = call.argument<Map<String, Any>>("filters")

                executor.execute {
                    val data =
                        if (isSaf) listSafDirectory(target.toUri(), filters)
                        else listNativeDirectory(File(target), filters)

                    mainHandler.post {
                        result.success(data)   // ✅ SAFE
                    }
                }

            }

            /**
             * Recursively traverses a directory tree.
             *
             * Used for:
             * - Search
             * - Indexing
             * - Analytics
             */
            "traverseDirectory" -> {
                val target = call.argument<String>("target") ?: return
                val isSaf = call.argument<Boolean>("isSaf") ?: false
                val maxDepth = call.argument<Int>("maxDepth") ?: 10
                val filters = call.argument<Map<String, Any>>("filters")

                executor.execute {
                    val out = mutableListOf<Map<String, Any?>>()
                    if (isSaf) {
                        traverseSaf(
                            DocumentFile.fromTreeUri(context, target.toUri()),
                            0, maxDepth, filters, out
                        )
                    } else {
                        traverseNative(File(target), 0, maxDepth, filters, out)
                    }
                    mainHandler.post {
                        result.success(out)   // ✅ SAFE
                    }
                }
            }

            /**
             * Opens the system SAF folder picker.
             *
             * Used when:
             * - Native paths are restricted
             * - USB / SD is SAF-only
             * - Play Store compliance requires scoped access
             */
            "openSafFolderPicker" -> {
                openSafPicker()
                result.success(true)
            }

            /**
             * Checks if MANAGE_EXTERNAL_STORAGE is granted.
             */
            "hasAllFilesAccess" ->
                result.success(hasAllFilesAccess())

            /**
             * Opens system settings screen to grant file manager access.
             */
            "requestAllFilesAccess" -> {
                requestAllFilesAccess()
                result.success(true)
            }

            /**
             * Returns OEM information.
             *
             * Useful for debugging OEM-specific restrictions.
             */
            "detectOEM" ->
                result.success(detectOEM())

            /**
             * High-level permission & environment diagnostics.
             */
            "permissionHealthCheck" ->
                result.success(permissionHealthCheck())

            else -> result.notImplemented()
        }
    }


    // Native storage roots
    // ─────────────────────────────────────────────
    private fun addRoot(
        roots: MutableList<Map<String, Any?>>,
        name: String,
        dir: File
    ) {
        try {
            val stat = StatFs(dir.absolutePath)
            roots.add(
                mapOf(
                    "type" to "native",
                    "name" to name,
                    "path" to dir.absolutePath,
                    "total" to stat.totalBytes,
                    "free" to stat.availableBytes,
                    "used" to stat.totalBytes - stat.availableBytes,
                    "writable" to dir.canWrite()
                )
            )
        } catch (_: Exception) {
            // Ignore broken / inaccessible mounts
        }
    }

    private fun getNativeRoots(): List<Map<String, Any?>> {
        val roots = mutableListOf<Map<String, Any?>>()
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // ✅ API 30+
            sm.storageVolumes.forEach { vol ->
                val dir = vol.directory ?: return@forEach
                addRoot(roots, vol.getDescription(context), dir)
            }
        } else {
            // ✅ API 24–29 (fallback strategy)

            // Primary external storage
            val primary = Environment.getExternalStorageDirectory()
            addRoot(roots, "Internal storage", primary)

            // Secondary storage (SD / USB)
            val secondaryRoots = arrayOf(
                File("/storage"),
                File("/mnt")
            )

            secondaryRoots.forEach { base ->
                base.listFiles()?.forEach { f ->
                    if (
                        f.exists() &&
                        f.isDirectory &&
                        f.canRead() &&
                        f.absolutePath != primary.absolutePath &&
                        !f.absolutePath.contains("emulated")
                    ) {
                        addRoot(roots, "External storage", f)
                    }
                }
            }
        }
        return roots
    }


    private fun getUnifiedRoots(): List<Map<String, Any?>> =
        getNativeRoots() + getSafRoots()

    // ─────────────────────────────────────────────
    // Filters
    // ─────────────────────────────────────────────

    private fun matchesFilters(
        size: Long,
        modified: Long,
        name: String,
        mime: String?,
        filters: Map<String, Any>?
    ): Boolean {
        if (filters == null) return true

        val minSize = (filters["minSize"] as? Number)?.toLong()
        val maxSize = (filters["maxSize"] as? Number)?.toLong()
        val after = (filters["modifiedAfter"] as? Number)?.toLong()
        val before = (filters["modifiedBefore"] as? Number)?.toLong()
        val exts = filters["extensions"] as? List<*>
        val mimes = filters["mimeTypes"] as? List<*>

        if (minSize != null && size < minSize) return false
        if (maxSize != null && size > maxSize) return false
        if (after != null && modified < after) return false
        if (before != null && modified > before) return false

        if (exts != null && exts.isNotEmpty()) {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext !in exts.map { it.toString().lowercase(Locale.US) }) return false
        }

        if (mimes != null && mime != null) {
            val ok = mimes.any {
                val m = it.toString()
                m == mime || (m.endsWith("/*") && mime.startsWith(m.dropLast(1)))
            }
            if (!ok) return false
        }
        return true
    }

    // ─────────────────────────────────────────────
    // Directory listing + traversal
    // ─────────────────────────────────────────────

    private fun listNativeDirectory(
        dir: File,
        filters: Map<String, Any>?
    ): List<Map<String, Any?>> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles()?.mapNotNull { f ->
            val size = if (f.isFile) f.length() else 0L
            val modified = f.lastModified()
            val mime = if (f.isFile)
                context.contentResolver.getType(Uri.fromFile(f))
            else null

            if (!matchesFilters(size, modified, f.name, mime, filters)) return@mapNotNull null

            mapOf(
                "name" to f.name,
                "path" to f.absolutePath,
                "uri" to null,
                "isDirectory" to f.isDirectory,
                "size" to size,
                "lastModified" to modified,
                "mime" to mime,
                "storageType" to "native"
            )
        } ?: emptyList()
    }

    private fun traverseNative(
        file: File,
        depth: Int,
        maxDepth: Int,
        filters: Map<String, Any>?,
        out: MutableList<Map<String, Any?>>
    ) {
        if (!file.exists() || depth > maxDepth) return

        val size = if (file.isFile) file.length() else 0L
        val modified = file.lastModified()
        val mime = if (file.isFile)
            context.contentResolver.getType(Uri.fromFile(file))
        else null

        if (matchesFilters(size, modified, file.name, mime, filters)) {
            out.add(
                mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "uri" to null,
                    "isDirectory" to file.isDirectory,
                    "size" to size,
                    "lastModified" to modified,
                    "mime" to mime,
                    "storageType" to "native"
                )
            )
        }

        if (file.isDirectory) {
            file.listFiles()?.forEach {
                traverseNative(it, depth + 1, maxDepth, filters, out)
            }
        }
    }

    private fun listSafDirectory(
        uri: Uri,
        filters: Map<String, Any>?
    ): List<Map<String, Any?>> {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()

        return root.listFiles().mapNotNull { doc ->
            val size = doc.length()
            val modified = doc.lastModified()
            val mime = doc.type

            if (!matchesFilters(size, modified, doc.name ?: "", mime, filters))
                return@mapNotNull null

            mapOf(
                "name" to (doc.name ?: ""),
                "path" to null,
                "uri" to doc.uri.toString(),
                "isDirectory" to doc.isDirectory,
                "size" to size,
                "lastModified" to modified,
                "mime" to mime,
                "storageType" to "saf"
            )
        }
    }

    private fun traverseSaf(
        doc: DocumentFile?,
        depth: Int,
        maxDepth: Int,
        filters: Map<String, Any>?,
        out: MutableList<Map<String, Any?>>
    ) {
        if (doc == null || depth > maxDepth) return

        val size = doc.length()
        val modified = doc.lastModified()
        val mime = doc.type

        if (matchesFilters(size, modified, doc.name ?: "", mime, filters)) {
            out.add(
                mapOf(
                    "name" to (doc.name ?: ""),
                    "path" to null,
                    "uri" to doc.uri.toString(),
                    "isDirectory" to doc.isDirectory,
                    "size" to size,
                    "lastModified" to modified,
                    "mime" to mime,
                    "storageType" to "saf"
                )
            )
        }

        if (doc.isDirectory) {
            doc.listFiles().forEach {
                traverseSaf(it, depth + 1, maxDepth, filters, out)
            }
        }
    }

    // ─────────────────────────────────────────────
    // SAF helpers
    // ─────────────────────────────────────────────

    private fun openSafPicker() {
        activity?.startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            },
            SAF_REQUEST_CODE
        )
    }

    private fun persistSafPermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private fun getSafRoots(): List<Map<String, Any?>> {
        val roots = mutableListOf<Map<String, Any?>>()
        context.contentResolver.persistedUriPermissions.forEach { perm ->
            val doc = DocumentFile.fromTreeUri(context, perm.uri) ?: return@forEach
            roots.add(
                mapOf(
                    "type" to "saf",
                    "name" to (doc.name ?: "SAF Folder"),
                    "uri" to perm.uri.toString(),
                    "writable" to perm.isWritePermission
                )
            )
        }
        return roots
    }

    // ─────────────────────────────────────────────
    // Permissions + diagnostics
    // ─────────────────────────────────────────────

    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        activity?.startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                "package:${activity?.packageName}".toUri()
            )
        )
    }

    private fun detectOEM(): Map<String, String> =
        mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "sdk" to Build.VERSION.SDK_INT.toString()
        )

    private fun permissionHealthCheck(): Map<String, Any> =
        mapOf(
            "allFilesAccess" to hasAllFilesAccess(),
            "sdk" to Build.VERSION.SDK_INT,
            "oem" to Build.MANUFACTURER
        )

    // ─────────────────────────────────────────────
    // USB listener
    // ─────────────────────────────────────────────

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_MEDIA_MOUNTED ->
                    channel.invokeMethod("onUsbAttached", null)
                Intent.ACTION_MEDIA_REMOVED,
                Intent.ACTION_MEDIA_UNMOUNTED ->
                    channel.invokeMethod("onUsbDetached", null)
            }
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addDataScheme("file")
        }
        context.registerReceiver(usbReceiver, filter)
    }

    private fun unregisterUsbReceiver() {
        try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }
}
