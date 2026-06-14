package net.ounben.AMARadio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import net.ounben.AMARadio.utils.UiScaler

class SplashActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScaler.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Show splash for a short time and then start ActivityMain
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, ActivityMain::class.java)
            val currentIntent = getIntent()
            if (currentIntent != null && currentIntent.extras != null) {
                intent.putExtras(currentIntent.extras!!)
            }
            startActivity(intent)
            finish()
        }, 1500)
    }
}
