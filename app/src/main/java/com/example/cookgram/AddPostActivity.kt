package com.example.cookgram

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cookgram.databinding.ActivityAddPostBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream

class AddPostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPostBinding
    private var pickedImage: Uri? = null

    companion object {
        private const val REQ_PICK_IMAGE = 101
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance() }


    private fun readBitmap(uri: Uri): Bitmap {
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open image" }
            return BitmapFactory.decodeStream(input)
        }
    }
    private fun scaleDown(src: Bitmap, maxDim: Int = 1024): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val s = kotlin.math.min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(src, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), true)
    }
    private fun bitmapToJpegBytes(bmp: Bitmap, quality: Int = 85): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
    private fun bytesToBase64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<ImageButton>(R.id.backBtn)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.pickImageButton.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(i, REQ_PICK_IMAGE)
        }

        binding.postButton.setOnClickListener {
            val uid = auth.currentUser?.uid
            val caption = binding.captionEditText.text?.toString()?.trim().orEmpty()
            val uri = pickedImage
            if (uid == null) { toast("Please log in"); return@setOnClickListener }
            if (caption.isEmpty() || uri == null) { toast("Pick an image and add a caption"); return@setOnClickListener }
            uploadPostBase64(uid, caption, uri)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            pickedImage = data?.data
            pickedImage?.let { Glide.with(this).load(it).centerCrop().into(binding.previewImage) }
        }
    }

    private fun uploadPostBase64(uid: String, caption: String, uri: Uri) {
        val dialog = ProgressDialog(this).apply { setCancelable(false); setMessage("Posting…"); show() }
        try {
            val bmp = readBitmap(uri)
            val scaled = scaleDown(bmp, 1024)
            val bytes = bitmapToJpegBytes(scaled, 85)
            val base64 = bytesToBase64(bytes)

            db.reference.child("Users").child(uid).get().addOnSuccessListener { u ->
                val username = u.child("username").getValue(String::class.java)
                    ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
                    ?: "User"
                val avatarBase64 = u.child("profileImageBase64").getValue(String::class.java)

                val postId = db.reference.child("Posts").push().key ?: System.currentTimeMillis().toString()
                val post = mapOf(
                    "postId" to postId,
                    "userId" to uid,
                    "username" to username,
                    "userImageBase64" to avatarBase64,
                    "imageBase64" to base64,
                    "caption" to caption,
                    "timestamp" to System.currentTimeMillis()
                )

                db.reference.child("Posts").child(postId).setValue(post)
                    .addOnSuccessListener {
                        dialog.dismiss(); toast("Posted!"); finish()
                    }
                    .addOnFailureListener { e ->
                        dialog.dismiss(); toast("DB error: ${e.message}")
                    }
            }.addOnFailureListener { e ->
                dialog.dismiss(); toast("User read error: ${e.message}")
            }
        } catch (e: Exception) {
            dialog.dismiss(); toast("Image error: ${e.message}")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
