package com.united.digitaldispatch.Login

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.ModuleType
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.utils.DebugSeeder
import com.united.digitaldispatch.utils.PasswordUtils
import com.united.digitaldispatch.utils.SessionManager
import java.util.concurrent.Executors

/**
 * Unified login: employee id + password + module (TAP/PPD/GLT/WH) +
 * organization. Validation rules mirror the old WH/PPD LoginActivity
 * (see [LoginValidator]) -- the difference here is that ONE screen now
 * handles all four modules instead of each module being its own app.
 *
 * The API isn't ready yet, so credential checking runs entirely against
 * the local Room tables (see [AppDatabase]), seeded for now by
 * [DebugSeeder]. Swapping in real API calls later only touches
 * attemptLogin() below -- the validation and dropdown logic doesn't
 * need to change.
 */

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmployee: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvDropdownOne: TextView // module
    private lateinit var tvDropdownTwo: TextView // organization

    private lateinit var db: AppDatabase
    private lateinit var session: SessionManager
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var selectedModule: String? = null
    private var selectedOrganization: OrganizationEntity? = null
    private var organizationsForModule: List<OrganizationEntity> = emptyList()
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        db = AppDatabase.getInstance(applicationContext)
        session = SessionManager(applicationContext)

        bindViews()
        setupPasswordToggle()
        setupModuleDropdown()
        setupOrganizationDropdown()

        findViewById<AppCompatButton>(R.id.btnLogin).setOnClickListener {
            onLoginClicked()
        }

        ioExecutor.execute {
            // TEMPORARY until the sync API is ready -- see DebugSeeder for details.
            DebugSeeder.seedIfEmpty(db)
        }
    }

    private fun bindViews() {
        etEmployee = findViewById(R.id.etEmployee)
        etPassword = findViewById(R.id.etPassword)
        tvDropdownOne = findViewById(R.id.tvDropdownOne)
        tvDropdownTwo = findViewById(R.id.tvDropdownTwo)

        tvDropdownOne.text = "Select Module"
        tvDropdownTwo.text = "Select Organization"
    }

    private fun setupPasswordToggle() {
        val imgShowPassword = findViewById<ImageView>(R.id.imgShowPassword)
        imgShowPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(etPassword.text.length)
        }
    }

    private fun setupModuleDropdown() {
        findViewById<android.view.View>(R.id.dropdownOne).setOnClickListener {
            val modules = ModuleType.ALL.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Module")
                .setItems(modules) { _, which ->
                    selectedModule = modules[which]
                    tvDropdownOne.text = selectedModule

                    // Changing the module invalidates whatever organization
                    // was picked before, same as the old spinner cascade.
                    selectedOrganization = null
                    tvDropdownTwo.text = "Select Organization"
                    loadOrganizationsForSelectedModule()
                }
                .show()
        }
    }

    private fun setupOrganizationDropdown() {
        findViewById<android.view.View>(R.id.dropdownTwo).setOnClickListener {
            val module = selectedModule
            if (module == null) {
                Toast.makeText(this, "Please select a module first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (organizationsForModule.isEmpty()) {
                Toast.makeText(this, "No organizations found for $module yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val names = organizationsForModule
                .map { "${it.organizationCode} - ${it.organizationName ?: ""}" }
                .toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Select Organization")
                .setItems(names) { _, which ->
                    selectedOrganization = organizationsForModule[which]
                    tvDropdownTwo.text = names[which]
                }
                .show()
        }
    }

    private fun loadOrganizationsForSelectedModule() {
        val module = selectedModule ?: return
        ioExecutor.execute {
            val orgs = db.organizationDao().getOrganizationsForModule(module)
            runOnUiThread {
                organizationsForModule = orgs
                if (orgs.isEmpty()) {
                    Toast.makeText(
                        this,
                        "No organizations synced for $module yet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun onLoginClicked() {
        val employeeId = etEmployee.text.toString().trim()
        val password = etPassword.text.toString()

        val error = LoginValidator.validate(
            employeeId = employeeId,
            password = password,
            selectedModule = selectedModule,
            selectedOrganizationCode = selectedOrganization?.organizationCode
        )
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return
        }

        attemptLogin(employeeId, password)
    }

    /**
     * Offline credential check against the local Room tables. Split into
     * "employee not found" vs "wrong password" so the user gets the same
     * distinct feedback the old apps gave -- not a generic "invalid login".
     *
     * When the API is ready, this is the one function that changes: it'll
     * call the login endpoint instead of userDao().findByEmployeeCodeAndOrg,
     * then optionally fall back to this same local check for offline use.
     */
    private fun attemptLogin(employeeId: String, password: String) {
        val organization = selectedOrganization ?: return
        val module = selectedModule ?: return

        ioExecutor.execute {
            val user = db.userDao().findByEmployeeCodeAndOrg(employeeId, organization.organizationCode)
            val result: LoginResult = when {
                user == null -> LoginResult.NotFound
                user.passwordHash != PasswordUtils.hash(password) -> LoginResult.WrongPassword
                else -> LoginResult.Success(user.userName, user.userRights)
            }

            runOnUiThread {
                when (result) {
                    is LoginResult.NotFound -> {
                        Toast.makeText(this, "Employee ID not found for this organization", Toast.LENGTH_SHORT).show()
                        etEmployee.setText("")
                    }
                    is LoginResult.WrongPassword -> {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                        etPassword.setText("")
                    }
                    is LoginResult.Success -> {
                        session.saveSession(
                            employeeCode = employeeId,
                            userName = result.userName,
                            organizationCode = organization.organizationCode,
                            moduleType = module,
                            userRights = result.userRights
                        )
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, Dashboard::class.java))
                        finish()
                    }
                }
            }
        }
    }

    private sealed class LoginResult {
        object NotFound : LoginResult()
        object WrongPassword : LoginResult()
        data class Success(val userName: String?, val userRights: String?) : LoginResult()
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}
