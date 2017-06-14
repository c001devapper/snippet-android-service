public class HasInternetConnectionBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = HasInternetConnectionBroadcastReceiver.class.getSimpleName();

    public HasInternetConnectionBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Broadcast has internet connection - on receive");
        Log.d(TAG, "Intent action: " + intent.getAction());
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {
            Log.d(TAG, "Запуск сохранения всех форм из broadcast receiver");
            GlobalSaveFormService.startDownloadAllFormOnServer(context);
            if (Settings.UPDATE_THESAURUS) {
                Log.d(TAG, "Запуск продолжения загрузки для справочников");
                ThesaurusUpdateService.resumeUpdateThesaurusVersions(context);
            }
        } else {
            Log.d(TAG, "wifi not connected");
        }

    }
}
