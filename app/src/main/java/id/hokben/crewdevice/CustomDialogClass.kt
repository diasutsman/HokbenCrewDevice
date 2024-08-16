package id.hokben.crewdevice

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import id.hokben.crewdevice.databinding.CustomDialogBinding

class CustomDialogClass(private val activity: Activity, private val onDismiss: (() -> Unit)? = null) : Dialog(activity) {

    init {
        setCancelable(false)
    }

    private lateinit var binding: CustomDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = CustomDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val sharedPref = activity.getPreferences(Context.MODE_PRIVATE)

        binding.edtIp.setText(sharedPref.getString(activity.getString(R.string.local_ip_key), ""))
        binding.edtPort.setText(
            sharedPref.getString(activity.getString(R.string.local_port_key), "3030")
        )

        binding.btnCancel.setOnClickListener {
            if (!validateInput()) {
                return@setOnClickListener
            }
            dismiss()
            onDismiss?.invoke()
        }
        binding.btnOk.setOnClickListener {
            if (!validateInput()) {
                return@setOnClickListener
            }

            with(sharedPref.edit()) {
                putString(activity.getString(R.string.local_ip_key), binding.edtIp.text.toString())
                putString(
                    activity.getString(R.string.local_port_key),
                    binding.edtPort.text.toString()
                )
                apply()
            }
            Toast.makeText(activity, "Berhasil mengubah ip local", Toast.LENGTH_LONG).show()
            dismiss()
            onDismiss?.invoke()
        }
    }

    private fun validateInput(): Boolean {
        if (binding.edtIp.text.toString().isBlank()) {
            Toast.makeText(activity, "Masukkan ip local", Toast.LENGTH_LONG).show()
            return false
        }

        if (binding.edtPort.text.toString().isBlank()) {
            Toast.makeText(activity, "Masukkan port local", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }
}