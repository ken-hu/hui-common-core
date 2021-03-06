package com.hui.common.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <code>HttpUtils</code>
 * <desc>
 * 描述：
 * <desc/>
 * Creation Time: 2019/12/7 15:51.
 *
 * @author Gary.Hu
 */
@Slf4j
public enum RestUtils {
    /**
     * init single
     */
    INSTANCE;
    /**
     * 连接超时时间
     */
    private static final int CONNECTION_TIME_OUT = 10000;
    /**
     * 读写超时时间
     */
    private static final int SOCKET_TIME_OUT = 15000;
    /**
     * 空闲连接数
     */
    private static final int MAX_IDLE_CONNECTIONS = 30;
    /**
     * 保持连接时间
     */
    private static final long KEEP_ALIVE_TIME = 60000L;

    @Getter
    private OkHttpClient okHttpClient;

    RestUtils() {
        ConnectionPool connectionPool = new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS);
        this.okHttpClient = new OkHttpClient().newBuilder()
                .readTimeout(SOCKET_TIME_OUT, TimeUnit.MILLISECONDS)
                .writeTimeout(SOCKET_TIME_OUT, TimeUnit.MILLISECONDS)
                .connectionPool(connectionPool)
                //自动重连设置为false
                .retryOnConnectionFailure(true)
                .connectTimeout(CONNECTION_TIME_OUT, TimeUnit.MILLISECONDS)
                //重试拦截器3次
                .addInterceptor(new RetryInterceptor(5))
                .build();
    }

    public interface NetCallBack {
        default void success(Call call, Response response) throws IOException {
            System.out.println(response.body().string());
        }

        default void failed(Call call, IOException e) {
            e.printStackTrace();
        }
    }

    public interface ProcessCallBack {
        default void process(int progress) {
            System.out.println(("File download process: >> " + progress + "%"));
        }
    }

    public String httpGet(String url) throws IOException {
        return httpGet(url, new HashMap<>());
    }

    public String httpGet(String url, Map<String, String> headers) throws IOException {
        Headers ofHeaders = headersHandler(headers);
        Request request = new Request.Builder().url(url).headers(ofHeaders).get().build();
        Response response = this.okHttpClient.newCall(request).execute();
        return response.body().string();
    }


    public String httpPost(String url, String requestBodyJson) throws IOException {
        return httpPost(url, requestBodyJson, new HashMap<>());
    }

    public String httpPost(String url, String requestBodyJson, Map<String, String> headers) throws IOException {
        Headers ofHeaders = Headers.of(headers);
        if (null == headers) {
            headers.put("Content-Type", "application/json;charset=utf-8");
        }
        RequestBody requestBody =
                RequestBody.create(requestBodyJson, MediaType.parse("application/json;charset=utf-8"));
        Request request = new Request.Builder().headers(ofHeaders).url(url).post(requestBody).build();
        Response response = this.okHttpClient.newCall(request).execute();
        return response.body().string();
    }


    public void httpGetAsync(String url, NetCallBack netCallBack) throws IOException {
        httpGetAsync(url, new HashMap<>(), netCallBack);
    }

    public void httpGetAsync(String url, Map<String, String> headers, NetCallBack netCallBack) throws IOException {
        Headers ofHeaders = headersHandler(headers);
        Request request = new Request.Builder().url(url).headers(ofHeaders).get().build();
        this.okHttpClient.newCall(request).enqueue(callbackHandler(netCallBack));
    }


    public void httpPostAsync(String url, String requestBodyJson, NetCallBack netCallBack) {
        httpPostAsync(url, requestBodyJson, new HashMap<>(), netCallBack);
    }

    public void httpPostAsync(String url, String requestBodyJson, Map<String, String> headers, NetCallBack netCallBack) {
        Headers ofHeaders = headersHandler(headers);
        RequestBody requestBody = RequestBody.create(requestBodyJson, MediaType.parse("application/json;charset=utf-8"));
        Request request = new Request.Builder().url(url).headers(ofHeaders).post(requestBody).build();
        this.okHttpClient.newCall(request).enqueue(callbackHandler(netCallBack));
    }

    public void downloadFile(String url, String destFile, String fileName, NetCallBack netCallBack) {
        downloadFile(url, destFile, fileName, netCallBack, null);
    }

    public void downloadFile(String url, String destDir, String fileName, NetCallBack netCallBack, ProcessCallBack processCallBack) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        this.okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                netCallBack.failed(call, e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                byte[] buf = new byte[2048];
                int len = 0;
                //储存下载文件的目录
                File dir = new File(destDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, fileName);

                try (
                        InputStream is = response.body().byteStream();
                        FileOutputStream fos = new FileOutputStream(file);
                ) {

                    long total = response.body().contentLength();
                    long sum = 0;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        sum += len;
                        int progress = (int) (sum * 1.0f / total * 100);
                        //下载中更新进度条
                        if (null != processCallBack) {
                            processCallBack.process(progress);
                        }
                    }
                    fos.flush();
                    //下载完成
                    netCallBack.success(call, response);
                } catch (IOException e) {
                    netCallBack.failed(call, e);
                }
            }
        });
    }

    private Headers headersHandler(Map<String, String> headers) {
        if (null == headers || headers.size() == 0) {
            headers.put("Content-Type", "application/json;charset=utf-8");
        }
        Headers ofHeaders = Headers.of(headers);
        return ofHeaders;
    }

    private Callback callbackHandler(final NetCallBack netCallBack) {
        return new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                netCallBack.failed(call, e);

            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                netCallBack.success(call, response);
            }
        };
    }


}
