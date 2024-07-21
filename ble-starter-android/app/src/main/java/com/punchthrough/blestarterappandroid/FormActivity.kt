class InputFormActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.input_form)

        val nameEditText: EditText = findViewById(R.id.et_name)
        val emailEditText: EditText = findViewById(R.id.et_email)
        val phoneEditText: EditText = findViewById(R.id.et_phone)
        val submitButton: Button = findViewById(R.id.btn_submit)

        submitButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val email = emailEditText.text.toString()
            val phone = phoneEditText.text.toString()

            // Handle the form submission (e.g., validation, send data to server, etc.)
            if (name.isNotEmpty() && email.isNotEmpty() && phone.isNotEmpty()) {
                // Example: Show a Toast message
                Toast.makeText(this, "Name: $name\nEmail: $email\nPhone: $phone", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
