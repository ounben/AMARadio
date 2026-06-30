package com.ounben.amaradio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.ui.AMARadioTheme
import com.ounben.amaradio.ui.AmaradioAmber
import com.ounben.amaradio.utils.LocaleUtils
import com.ounben.amaradio.utils.UiScaler
import kotlinx.coroutines.delay
import androidx.preference.PreferenceManager

class SplashActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(newBase)
        val lang = sharedPref.getString("settings_language", "system") ?: "system"
        val localeContext = LocaleUtils.wrapContext(newBase, lang)
        super.attachBaseContext(UiScaler.wrapContext(localeContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AMARadioTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(R.drawable.ic_cat_face),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AMARadio",
                            color = AmaradioAmber,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v${stringResource(R.string.version_name)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    delay(1000)
                    val intent = Intent(this@SplashActivity, ActivityMain::class.java)
                    getIntent()?.extras?.let { intent.putExtras(it) }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
