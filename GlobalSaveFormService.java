public class GlobalSaveFormService extends Service {

    private static final String TAG = GlobalSaveFormService.class.getSimpleName();

    private static final String ACTION_DOWNLOAD_ALL_FORM_ON_SERVER = TAG + ".DownloadAllFromOnServer";
    private static final String ACTION_DOWNLOAD_TARGET_FORM_ON_SERVER = TAG + ".DownloadTargetFromOnServer";
    private static final String EXTRA_TARGET_ID = TAG + ".extra.TargetFormId";
    private static final String ACTION_CHECK_HAS_ACTIVE_LOADINGS_ON_SERVER = TAG + ".CheckHasActiveLoadingsOnServer";

    public static final String ACTION_RESULT_NO_ACTIVE_LOADINGS_ON_SERVER = TAG + ".NoActiveLoadingsOnServer";
    public static final String ACTION_RESULT_HAS_ACTIVE_LOADINGS_ON_SERVER = TAG + ".HasActiveLoadingsOnServer";


    private ConcurrentHashMap<Long, Boolean> mConcurrentHashMap;

    private ServiceOperationHandlerThread mServiceOperationHandlerThread;

    private ExecutorService mExecutorService;
    private Handler mHandler;

    public static void checkHasActiveLoadingsOnServer(Context context) {
        Intent intent = new Intent(context, GlobalSaveFormService.class);
        intent.setAction(ACTION_CHECK_HAS_ACTIVE_LOADINGS_ON_SERVER);
        context.startService(intent);
    }


    public static void startDownloadAllFormOnServer(Context context) {
        Intent intent = new Intent(context, GlobalSaveFormService.class);
        intent.setAction(ACTION_DOWNLOAD_ALL_FORM_ON_SERVER);
        context.startService(intent);
    }

    public static void startDownloadTargetFormOnServer(Context context, long targetId) {
        Intent intent = new Intent(context, GlobalSaveFormService.class);
        intent.setAction(ACTION_DOWNLOAD_TARGET_FORM_ON_SERVER);
        intent.putExtra(EXTRA_TARGET_ID, targetId);
        context.startService(intent);
    }

    public GlobalSaveFormService() {
    }

    private static final int LOAD_ALL = 0;
    private static final int START_LOADING = 2;
    private static final int LOADING_COMPLETE = 3;
    private static final int LOADING_FAILED = 4;
    private static final int CHECK_ACTIVE_LOADINGS = 5;

    @Override
    public void onCreate() {
        Log.d(TAG, "Service created: " + this);
        mConcurrentHashMap = new ConcurrentHashMap<>();
        mExecutorService = Executors.newCachedThreadPool();
        mServiceOperationHandlerThread = new ServiceOperationHandlerThread();
        mServiceOperationHandlerThread.start();
        mHandler = new MyHandler(mServiceOperationHandlerThread.getLooper());
    }

    private class MyHandler extends Handler {

        private MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LOAD_ALL:
                    Log.d(TAG, "Обработка сообщения загрузить всё");
                    loadAll();
                    break;
                case START_LOADING:
                    Log.d(TAG, "Обработка сообщения загрузить указанную форму id:" + msg.arg1);
                    startLoading(msg.arg1);
                    break;
                case LOADING_COMPLETE:
                    Log.d(TAG, "Обработка сообщения успешная загрузка формы с id:" + msg.arg1);
                    loadingComplete(msg.arg1);
                    break;
                case LOADING_FAILED:
                    Log.d(TAG, "Обработка сообщения ошибка загрузка формы с id:" + msg.arg1);
                    loadingFailed(msg.arg1);
                    break;
                case CHECK_ACTIVE_LOADINGS:
                    Log.d(TAG, "Обработка сообщения проверка наличия загружаемых данных на сервер");
                    checkActiveLoadings();
                    break;
            }
        }

        private boolean isAbsentTargetInLoadingQueue(long targetId) {
            return !mConcurrentHashMap.containsKey(targetId);
        }

        private void loadAll() {
            Log.d(TAG, "loadAll():");
            List<AutoForm> autoFormList = AutoFormRepository.getAll(getApplication());
            if (!autoFormList.isEmpty()) {
                Log.d(TAG, "Имеються формы для сохранения количество: " + autoFormList.size());
                for (AutoForm autoForm : autoFormList) {
                    long targetAutoFormId = autoForm.getId();
                    if (isAbsentTargetInLoadingQueue(targetAutoFormId)) {
                        Log.d(TAG, "Отправка сообщения для сохранения формы с id: " + targetAutoFormId);
                        mHandler.obtainMessage(START_LOADING, (int) targetAutoFormId, 0).sendToTarget();
                    }
                }
            } else {
                Log.d(TAG, "Нет форм для сохранения в базе");
            }

            if (mConcurrentHashMap.size() > 0) {
                Log.d(TAG, "Повтрорная попытка отправки данныx - тех что есть в очереди но завершились неудачно");
                for (Map.Entry<Long, Boolean> entry : mConcurrentHashMap.entrySet()) {
                    Log.d(TAG, Striexample.javang.format("Обработка записи с id %s и статусом %s", entry.getKey(), entry.getValue()));
                    if (!entry.getValue()) {
                        Log.d(TAG, "Повторная попытка отправки данныx для записи с id:" + entry.getKey());
                        mHandler.obtainMessage(START_LOADING, Integer.parseInt(entry.getKey().toString()), 0).sendToTarget();
                    }
                }
            } else {
                Log.d(TAG, "Нет активных загрузок");
            }

        }

        private void startLoading(long targetId) {
            Log.d(TAG, "startLoading():");
            //если такого id нет в загрузках
            if (isAbsentTargetInLoadingQueue(targetId)) {
                mConcurrentHashMap.putIfAbsent(targetId, true);
                Log.d(TAG, "Запуск задачи сохранения через ExecutoService для записи " + targetId);
                mExecutorService.execute(new SaveFormRemoteTask(targetId, mLoadingCallback, getApplicationContext()));
            } else if (mConcurrentHashMap.containsKey(targetId) && !mConcurrentHashMap.get(targetId)) {
                //если элемент с таким id существует, но не загружается
                Log.d(TAG, "Повторная загрузка элемента: " + targetId);
                mConcurrentHashMap.put(targetId, true);
                Log.d(TAG, "Запуск задачи сохранения через ExecutoService для записи " + targetId);
                mExecutorService.execute(new SaveFormRemoteTask(targetId, mLoadingCallback, getApplicationContext()));
            }
        }

        private void loadingComplete(long completeTargetId) {
            try {
                Log.d(TAG, "Удаление записи с id " + completeTargetId);
                AutoFormRepository.delete(getApplicationContext(), completeTargetId);
                Log.d(TAG, "Перед удалением формы кол-во записей " + mConcurrentHashMap.size());
                mConcurrentHashMap.remove(completeTargetId);
                Log.d(TAG, "Осталось  форм загрузить: " + mConcurrentHashMap.size());
            } catch (Exception e) {
                Log.d(TAG, "Error when try delete record with id: " + completeTargetId + " error: " + e.getMessage());
            }
        }

        private void loadingFailed(long failedTargetId) {
            if (mConcurrentHashMap.containsKey(failedTargetId)) {
                mConcurrentHashMap.put(failedTargetId, false);
                Log.d(TAG, "Статус для записи с id " + failedTargetId + "изменился на незагружаемый");
                Log.d(TAG, "Осталось  форм загрузить: " + mConcurrentHashMap.size());
            }
        }

        private void checkActiveLoadings() {
            Intent result = new Intent();
            if (hasActiveLoadings()) {
                Log.d(TAG, "checkActiveLoadings(): имеются активные загрузки - отправка оповещения");
                result.setAction(ACTION_RESULT_HAS_ACTIVE_LOADINGS_ON_SERVER);
                getApplicationContext().sendBroadcast(result);
            } else {
                result.setAction(ACTION_RESULT_NO_ACTIVE_LOADINGS_ON_SERVER);
                Log.d(TAG, "checkActiveLoadings(): активных загрузкок нет. Количество неактивных элементов: " + mConcurrentHashMap.size());
                getApplicationContext().sendBroadcast(result);
                //todo -  kill save resource
                // stopSelf();
            }
        }

        private boolean hasActiveLoadings() {
            for (Map.Entry<Long, Boolean> entry : mConcurrentHashMap.entrySet()) {
                if (entry.getValue()) {
                    return true;
                }
            }
            return false;
        }

    }

    @Override
    public void onDestroy() {
        mServiceOperationHandlerThread.quit();
        mExecutorService.shutdown();
        AutoFormApplication.getInstance().cancel(SaveFormRemoteTask.REQUEST_TAG);
        Log.d(TAG, "Service call on destroy method");
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private class ServiceOperationHandlerThread extends HandlerThread {


        public ServiceOperationHandlerThread() {
            super("ServiceOperationHandlerThread", Process.THREAD_PRIORITY_BACKGROUND);

        }

        public void loadAll() {
            mHandler.sendEmptyMessage(LOAD_ALL);
        }

        public void onCompleteLoading(long completeTargetId) {
            mHandler.obtainMessage(LOADING_COMPLETE, (int) completeTargetId, 0).sendToTarget();
        }

        public void onFailedLoading(long failedTargetId) {
            mHandler.obtainMessage(LOADING_FAILED, (int) failedTargetId, 0).sendToTarget();

        }

        public void loadTarget(final long targetId) {
            mHandler.obtainMessage(START_LOADING, (int) targetId, 0).sendToTarget();
        }

        public void checkActiveLoadings() {
            mHandler.sendEmptyMessage(CHECK_ACTIVE_LOADINGS);
        }

        @Override
        public boolean quit() {
            mHandler.getLooper().quit();
            return true;
        }

    }

    private FormLoadingCallback mLoadingCallback = new FormLoadingCallback() {
        @Override
        public void onFormLoadingComplete(long recordId) {
            mServiceOperationHandlerThread.onCompleteLoading(recordId);
        }

        @Override
        public void onFormLoadingFailed(long recordId) {
            mServiceOperationHandlerThread.onFailedLoading(recordId);
        }
    };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_DOWNLOAD_TARGET_FORM_ON_SERVER.equals(action)) {
            Log.d(TAG, "Запуск сохранения выборанной формы");
            mServiceOperationHandlerThread.loadTarget(intent.getLongExtra(EXTRA_TARGET_ID, 0));
        } else if (ACTION_DOWNLOAD_ALL_FORM_ON_SERVER.equals(action)) {
            Log.d(TAG, "Запуск сохранения всех форм");
            mServiceOperationHandlerThread.loadAll();
        } else if (ACTION_CHECK_HAS_ACTIVE_LOADINGS_ON_SERVER.equals(action)) {
            mServiceOperationHandlerThread.checkActiveLoadings();
        }
        return START_NOT_STICKY;
    }


    public interface FormLoadingCallback {
        void onFormLoadingComplete(long recordId);

        void onFormLoadingFailed(long recordId);
    }


}
