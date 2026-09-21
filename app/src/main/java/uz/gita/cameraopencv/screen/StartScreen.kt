package uz.gita.cameraopencv.screen

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.permissionx.guolindev.PermissionX
import dev.androidbroadcast.vbpd.viewBinding
import uz.gita.cameraopencv.R
import uz.gita.cameraopencv.databinding.StartScreenBinding

class StartScreen: Fragment(R.layout.start_screen) {

    private val viewBinding by viewBinding(StartScreenBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        viewBinding.btnStart.setOnClickListener {
            openCamera()
        }

    }

    private fun openCamera() {
        PermissionX.init(this)
            .permissions(Manifest.permission.CAMERA)
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.main, CameraScreen())
                        .commit()
                } else {
                    Toast.makeText(requireContext(), "These permissions are denied: $deniedList", Toast.LENGTH_LONG).show()
                }
            }
    }
}