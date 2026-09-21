package uz.gita.cameraopencv.screen

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import dev.androidbroadcast.vbpd.viewBinding
import uz.gita.cameraopencv.R
import uz.gita.cameraopencv.databinding.ResultScreenBinding

class ResultScreen: Fragment(R.layout.result_screen) {

    private val viewBinding by viewBinding(ResultScreenBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewBinding.txtType.text = Repository.shape
        viewBinding.txtColor.text = Repository.color
    }

}