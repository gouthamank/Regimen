package dev.gouthaman.regimen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Health Connect requires an activity it can launch from its own Settings UI ("why does this app
 * want this data") - `ACTION_SHOW_PERMISSIONS_RATIONALE` pre-Android-14, the
 * `VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS` intent filter on Android 14+ (see
 * `AndroidManifest.xml`). Hands off to Regimen's actual hosted privacy policy rather than
 * duplicating an explanation in-app.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://regimen.gouthaman.dev/privacy-policy/")),
        )
        finish()
    }
}
