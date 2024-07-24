package com.punchthrough.blestarterappandroid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.punchthrough.blestarterappandroid.databinding.ActivitySignupBinding

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set a click listener for the submit button
        binding.submitButton.setOnClickListener {
            val name = binding.nameInput.text.toString()
            val gender = binding.genderInput.text.toString()
            val birthdate = binding.birthdateInput.text.toString()
            val height = binding.heightInput.text.toString().toFloatOrNull()
            val weight = binding.weightInput.text.toString().toFloatOrNull()
            val healthConditions = binding.healthConditionsInput.text.toString()
            val medications = binding.medicationsInput.text.toString()
            val notes = binding.notesInput.text.toString()
            val username = binding.username.text.toString()
            val password = binding.password.text.toString()
            val email = binding.email.text.toString()

            // Add your form submission logic here
            if (name.isNotBlank() && gender.isNotBlank() && birthdate.isNotBlank() && height != null && weight != null && username.isNotBlank() && password.isNotBlank() && email.isNotBlank()) {
                // Process the input data
                // For example, you could save it to a database or send it to a server
                // Show a confirmation message
                Toast.makeText(this, "Form submitted successfully", Toast.LENGTH_LONG).show()

                // Navigate to MainActivity
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()  // Optionally, close the SignupActivity

            } else {
                // Handle validation errors
                // Show an error message to the user
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
