package pl.qprogramming.magicmirror;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.util.Log;

import androidx.preference.PreferenceManager;
import pl.qprogramming.magicmirror.settings.Property;

/**
 * A {@link BroadcastReceiver} for {@link Intent#ACTION_BOOT_COMPLETED}.
 */
public class BootReceiver extends BroadcastReceiver {
  private static final String TAG = BootReceiver.class.getSimpleName();
  private static final String ACTION_DELAYED_START = "pl.qprogramming.magicmirror.DELAYED_START";

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    if (ACTION_DELAYED_START.equals(action)) {
      startHome(context);
      return;
    }
    if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
      Log.w(TAG, "Unexpected action:" + action);
      return;
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (!prefs.getBoolean(Property.AUTOSTART, true)) {
      Log.d(TAG, "Autostart disabled.");
      return;
    }
    // Launch HomeActivity once boot is complete. This is useful on platforms where we aren't
    // allowed to replace the launcher, e.g. an unrooted Fire TV Stick. It's not necessary if we are
    // already default for the home intent.
    if (isMirrorHome(context)) {
      Log.d(TAG, "Already using mirror home.");
      return;
    }
    long delayMinutes = 1;
    try {
      delayMinutes = Long.parseLong(prefs.getString(Property.AUTOSTART_DELAY, "1"));
    } catch (NumberFormatException e) {
      Log.w(TAG, "Invalid autostart delay, using 1 minute.");
    }
    scheduleStart(context, delayMinutes);
  }

  private void scheduleStart(Context context, long delayMinutes) {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent startIntent = new Intent(context, BootReceiver.class).setAction(ACTION_DELAYED_START);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, startIntent,
        PendingIntent.FLAG_UPDATE_CURRENT);
    long triggerAt = SystemClock.elapsedRealtime() + delayMinutes * 60_000L;
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt,
        pendingIntent);
    Log.d(TAG, "Mirror home scheduled in " + delayMinutes + " min.");
  }

  private void startHome(Context context) {
    Log.d(TAG, "Starting mirror home.");
    try {
      Intent homeIntent = new Intent(context, HomeActivity.class);
      homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(homeIntent);
    } catch (Exception e) {
      Log.e(TAG, "Failed to start mirror home", e);
    }
  }

  /**
   * Determines whether a mirror activity is already default for the home intent.
   */
  private boolean isMirrorHome(Context context) {
    Intent homeIntent = new Intent(Intent.ACTION_MAIN);
    homeIntent.addCategory(Intent.CATEGORY_HOME);
    PackageManager packageManager = context.getPackageManager();
    ResolveInfo homeInfo = packageManager.resolveActivity(homeIntent, 0);
    String homePackageName = homeInfo.activityInfo.packageName;
    return context.getPackageName().equals(homePackageName);
  }
}
