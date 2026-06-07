package net.ounben.AMARadio

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Show splash for a short time and then start ActivityMain
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, ActivityMain::class.java)
            intent.putExtras(getIntent())
            startActivity(intent)
            finish()
        }, 1500)
    }
}
