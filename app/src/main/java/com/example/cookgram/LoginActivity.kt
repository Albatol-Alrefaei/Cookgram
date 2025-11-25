package com.example.cookgram

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var rememberMeCheckBox: CheckBox
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        rememberMeCheckBox = findViewById(R.id.rememberMeCheckBox)
        sharedPreferences = getSharedPreferences("user_data", MODE_PRIVATE)

        val savedEmail = sharedPreferences.getString("email", "")
        val savedPassword = sharedPreferences.getString("password", "")
        if (!savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
            emailEditText.setText(savedEmail)
            passwordEditText.setText(savedPassword)
            rememberMeCheckBox.isChecked = true
        }

        findViewById<Button>(R.id.next_button).setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() && password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show()
            } else if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
            } else if (password.isEmpty()) {
                Toast.makeText(this, "Please enter your password.", Toast.LENGTH_SHORT).show()
            } else {
                login(email, password)
            }
        }

        findViewById<TextView>(R.id.register).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }


        findViewById<TextView>(R.id.ForgotPassword).setOnClickListener {
            showResetPasswordDialog()
        }
    }

    private fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()

                        if (rememberMeCheckBox.isChecked) {
                            val editor = sharedPreferences.edit()
                            editor.putString("email", email)
                            editor.putString("password", password)
                            editor.apply()
                        } else {
                            sharedPreferences.edit().clear().apply()
                        }

                        startActivity(Intent(this, FeedActivity::class.java))
                        finish()
                    } else {

                        auth.signOut()
                        Toast.makeText(
                            this,
                            "Please verify your email before logging in.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    when (val exception = task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> {
                            Toast.makeText(
                                this,
                                "Incorrect password or email. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                this,
                                "Login Failed: ${exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    private fun showResetPasswordDialog() {
        val input = EditText(this).apply {
            hint = "Enter your email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setView(input)
            .setPositiveButton("Send") { dialog, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
                } else {
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Password reset email sent.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Failed to send reset email: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
