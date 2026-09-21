package uz.gita.cameraopencv

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.opencv.android.OpenCVLoader
import uz.gita.cameraopencv.screen.ErrorScreen
import uz.gita.cameraopencv.screen.StartScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val startScreen = if (OpenCVLoader.initLocal()){
            StartScreen()
        }else{
            ErrorScreen()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.main, startScreen)
            .commit()
    }
}