class SaveFormRemoteTask implements Runnable {

    public static final String REQUEST_TAG = "saveFormRequestTag";
    private final String TASK_TAG = SaveFormRemoteTask.class.getSimpleName();
    private final long targetId;
    private final GlobalSaveFormService.FormLoadingCallback mCallback;
    private final Context mContext;

    SaveFormRemoteTask(long targetId, GlobalSaveFormService.FormLoadingCallback formLoadingCallback, Context context) {

        this.targetId = targetId;
        mCallback = formLoadingCallback;
        mContext = context;
    }

    @Override
    public void run() {
        Log.d(TASK_TAG, "Запуск SaveFormRemoteTask");
        AutoForm autoForm = AutoFormRepository.get(mContext, targetId);
        if (autoForm == null || autoForm.getId() == 0) {
            mCallback.onFormLoadingComplete(targetId);
            Log.d(TASK_TAG, "Выход из задачи : autoForm == null или id: " + targetId);
            return;
        }

        if (NetworkHelper.isNetworkAvailable(mContext)) {

            final Map<String, String> data = AutoFormConvector.toMap(autoForm);
            String path = UrlHolder.getFormUrl();
            StringRequest request = RequestCreator.getSaveFormRequest(path, data, new Response.Listener<String>() {
                @Override
                public void onResponse(String s) {
                    if (formIsSaved(s)) {
                        Log.d(TASK_TAG, "Удачный ответ с сервера:" + s + " форма подлежит удалению");
                        mCallback.onFormLoadingComplete(targetId);
                    } else {
                        Log.d(TASK_TAG, "Error when parse data from server or response is 0 response: " + s);
                        mCallback.onFormLoadingFailed(targetId);
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError volleyError) {
                    Log.e(TASK_TAG, "Error when loading data bad response from server: " + volleyError.getMessage());
                    mCallback.onFormLoadingFailed(targetId);
                }
            });
            Log.d(TASK_TAG, "Запуск сетевого запроса сохранения для записи :" + autoForm.getId());
            AutoFormApplication.getInstance().addRequest(request, REQUEST_TAG);

        } else {
            Log.d(TASK_TAG, "Нету интернета - вызов failed");
            mCallback.onFormLoadingFailed(targetId);
        }


    }


    private boolean formIsSaved(String serverStringResponse) {
        boolean result = false;
        try {
            Log.d(TASK_TAG, "formIsSaved(): Response from server:" + serverStringResponse);
            Long number = Long.parseLong(serverStringResponse);
            if (number > 0) {
                result = true;
            }
        } catch (Exception e) {
            Log.e(TASK_TAG, "formIsSaved(): Exception when parse response from server: " + e.getMessage());
        }
        return result;
    }
}
