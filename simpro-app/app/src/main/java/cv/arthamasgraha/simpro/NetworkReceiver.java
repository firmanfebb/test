package cv.arthamasgraha.simpro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver that listens for network connectivity changes.
 * When network becomes available, it notifies the MainActivity to auto-retry loading.
 */
public class NetworkReceiver extends BroadcastReceiver {

    public interface NetworkListener {
        void onNetworkAvailable();
        void onNetworkLost();
    }

    private NetworkListener listener;

    public NetworkReceiver() {
        // Required empty constructor for manifest declaration
    }

    public NetworkReceiver(NetworkListener listener) {
        this.listener = listener;
    }

    public void setListener(NetworkListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (listener == null) return;

        if (NetworkUtil.isNetworkAvailable(context)) {
            listener.onNetworkAvailable();
        } else {
            listener.onNetworkLost();
        }
    }
}
