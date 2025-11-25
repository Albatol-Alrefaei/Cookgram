package com.example.cookgram

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SettingsActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton?>(R.id.backBtn)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<Button>(R.id.btnChangeEmail).setOnClickListener { changeEmail() }
        findViewById<Button>(R.id.btnChangePassword).setOnClickListener { changePassword() }

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnDeleteAccount).setOnClickListener {
            deleteAccount()
        }
    }

    private fun changeEmail() {
        val user = auth.currentUser ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val currentPassInput = EditText(this).apply {
            hint = "Current password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newEmailInput = EditText(this).apply {
            hint = "New email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        container.addView(currentPassInput)
        container.addView(newEmailInput)

        AlertDialog.Builder(this)
            .setTitle("Change Email")
            .setView(container)
            .setPositiveButton("Save") { dialog, _ ->
                val currentPass = currentPassInput.text.toString().trim()
                val newEmail = newEmailInput.text.toString().trim()

                if (currentPass.isEmpty() || newEmail.isEmpty()) {
                    toast("Please fill in both fields")
                    return@setPositiveButton
                }

                val currentEmail = user.email
                if (currentEmail.isNullOrEmpty()) {
                    toast("No email found on this account")
                    return@setPositiveButton
                }

                val credential = EmailAuthProvider.getCredential(currentEmail, currentPass)

                user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        user.updateEmail(newEmail).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                val uid = user.uid
                                FirebaseDatabase.getInstance()
                                    .getReference("Users")
                                    .child(uid)
                                    .child("email")
                                    .setValue(newEmail)
                                    .addOnCompleteListener { dbTask ->
                                        if (dbTask.isSuccessful) {
                                            user.sendEmailVerification().addOnCompleteListener { verTask ->
                                                if (verTask.isSuccessful) {
                                                    toast("Email updated. Verification sent to $newEmail")
                                                } else {
                                                    toast("Email updated, but failed to send verification: ${verTask.exception?.message}")
                                                }
                                            }
                                        } else {
                                            toast("Email updated but failed to update profile in database: ${dbTask.exception?.message}")
                                            user.sendEmailVerification().addOnCompleteListener { verTask ->
                                                if (verTask.isSuccessful) {
                                                    toast("Verification sent to $newEmail")
                                                } else {
                                                    toast("Failed to send verification: ${verTask.exception?.message}")
                                                }
                                            }
                                        }
                                    }
                            } else {
                                toast("Failed: ${updateTask.exception?.message}")
                            }
                        }

                    } else {
                        toast("Wrong password: ${reauthTask.exception?.message}")
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun changePassword() {
        val user = auth.currentUser ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val currentPassInput = EditText(this).apply {
            hint = "Current password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPassInput = EditText(this).apply {
            hint = "New password (min 6 chars)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        container.addView(currentPassInput)
        container.addView(newPassInput)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(container)
            .setPositiveButton("Save") { dialog, _ ->
                val currentPass = currentPassInput.text.toString().trim()
                val newPass = newPassInput.text.toString().trim()

                if (currentPass.isEmpty() || newPass.isEmpty()) {
                    toast("Please fill in both fields")
                    return@setPositiveButton
                }

                if (newPass.length < 6) {
                    toast("Password too short")
                    return@setPositiveButton
                }

                val email = user.email
                if (email.isNullOrEmpty()) {
                    toast("No email found on this account")
                    return@setPositiveButton
                }

                val credential = EmailAuthProvider.getCredential(email, currentPass)

                user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                toast("Password updated")
                            } else {
                                toast("Failed: ${updateTask.exception?.message}")
                            }
                        }
                    } else {
                        toast("Wrong password: ${reauthTask.exception?.message}")
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return

        val passwordInput = EditText(this).apply {
            hint = "Enter your password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Delete account")
            .setMessage("Are you sure? This cannot be undone.")
            .setView(passwordInput)
            .setPositiveButton("Delete") { dialog, _ ->
                val password = passwordInput.text.toString().trim()
                if (password.isEmpty()) {
                    toast("Please enter your password")
                    return@setPositiveButton
                }

                val email = user.email
                if (email.isNullOrEmpty()) {
                    toast("No email found on this account")
                    return@setPositiveButton
                }

                val credential = EmailAuthProvider.getCredential(email, password)

                user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        val uid = user.uid
                        user.delete().addOnCompleteListener { deleteTask ->
                            if (deleteTask.isSuccessful) {
                                FirebaseDatabase.getInstance()
                                    .getReference("Users")
                                    .child(uid)
                                    .removeValue()

                                toast("Account deleted")
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            } else {
                                toast("Failed: ${deleteTask.exception?.message}")
                            }
                        }
                    } else {
                        toast("Wrong password: ${reauthTask.exception?.message}")
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
