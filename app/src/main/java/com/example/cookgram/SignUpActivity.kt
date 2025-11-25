package com.example.cookgram

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class SignUpActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        auth = FirebaseAuth.getInstance()

        val usernameEditText = findViewById<EditText>(R.id.usernameInput)
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)

        findViewById<Button>(R.id.next_button).setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            when {
                username.isEmpty() -> {
                    Toast.makeText(this, "Please enter your username.", Toast.LENGTH_SHORT).show()
                }
                email.isEmpty() -> {
                    Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
                }
                password.isEmpty() -> {
                    Toast.makeText(this, "Please enter your password.", Toast.LENGTH_SHORT).show()
                }
                !isEmailValid(email) -> {
                    Toast.makeText(this, "Invalid email format. Please enter a valid email.", Toast.LENGTH_SHORT).show()
                }
                !isPasswordStrong(password) -> {
                    Toast.makeText(
                        this,
                        "Password must be at least 8 characters long, contain a mix of uppercase, lowercase, digits, and special characters.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    signUp(username, email, password)
                }
            }
        }

        findViewById<TextView>(R.id.login).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun isEmailValid(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return email.matches(emailPattern.toRegex())
    }

    private fun isPasswordStrong(password: String): Boolean {
        val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!#%*?&=+])[A-Za-z\\d@$!#%*?&]{6,}$"
        return password.matches(passwordPattern.toRegex())
    }

    private fun signUp(username: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid

                    if (userId != null && user != null) {
                        saveUserToDatabase(userId, username, email)

                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()

                        user.updateProfile(profileUpdates).addOnCompleteListener {

                            user.sendEmailVerification()
                                .addOnCompleteListener { vTask ->
                                    if (vTask.isSuccessful) {
                                        Toast.makeText(
                                            this,
                                            "Sign-Up successful! Check your email to verify before logging in.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this,
                                            "Sign-Up successful, but failed to send verification email: ${vTask.exception?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }


                                    auth.signOut()
                                    startActivity(Intent(this, LoginActivity::class.java))
                                    finish()
                                }
                        }
                    }
                } else {
                    when (val exception = task.exception) {
                        is FirebaseAuthUserCollisionException -> {
                            Toast.makeText(
                                this,
                                "This email is already registered. Please use a different email or log in.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                this,
                                "Sign-Up Failed: ${exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    private fun saveUserToDatabase(userId: String, username: String, email: String) {
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)
        val userInfo = mapOf(
            "username" to username,
            "email" to email,
            "bio" to ""
        )

        userRef.setValue(userInfo).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Toast.makeText(
                    this,
                    "Failed to save user data: ${task.exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
