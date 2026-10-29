package ru.fermocontrol.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.fermocontrol.app.ui.theme.FermocontrolTheme

class MainActivity : ComponentActivity() {
    private var requestedProductId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedProductId = intent?.getStringExtra(ReminderScheduler.EXTRA_PRODUCT_ID)
        ReminderScheduler.schedulePeriodicCheck(applicationContext)
        enableEdgeToEdge()
        setContent {
            FermocontrolTheme {
                FermocontrolApp(
                    openProductId = requestedProductId,
                    onConsumeOpenProductId = { requestedProductId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(ReminderScheduler.EXTRA_PRODUCT_ID)?.let {
            requestedProductId = it
        }
    }
}
