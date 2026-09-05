package com.anxin.util;

import cn.hutool.json.JSONUtil;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP 客户端工具类（基于 Apache HttpClient 4.x）。
 */
public class HttpClient {

    static final int TIMEOUT_MSEC = 5 * 1000;

    /**
     * 发送 GET 请求。
     *
     * @param url      请求地址
     * @param paramMap 查询参数（可为 null）
     * @return 响应体字符串（UTF-8），非 200 返回 ""
     */
    public static String doGet(String url, Map<String, String> paramMap) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String result = "";
        CloseableHttpResponse response = null;
        try {
            URIBuilder builder = new URIBuilder(url);
            if (paramMap != null) {
                for (String key : paramMap.keySet()) {
                    builder.addParameter(key, paramMap.get(key));
                }
            }
            URI uri = builder.build();

            HttpGet httpGet = new HttpGet(uri);
            response = httpClient.execute(httpGet);

            if (response.getStatusLine().getStatusCode() == 200) {
                result = EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (response != null) response.close();
                httpClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 发送 POST 请求（表单格式 application/x-www-form-urlencoded）。
     *
     * @param url      请求地址
     * @param paramMap 表单参数（可为 null）
     * @return 响应体字符串（UTF-8）
     * @throws IOException 网络异常
     */
    public static String doPost(String url, Map<String, String> paramMap) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        String resultString = "";
        try {
            HttpPost httpPost = new HttpPost(url);

            if (paramMap != null) {
                List<NameValuePair> paramList = new ArrayList();
                for (Map.Entry<String, String> param : paramMap.entrySet()) {
                    paramList.add(new BasicNameValuePair(param.getKey(), param.getValue()));
                }
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList);
                httpPost.setEntity(entity);
            }
            httpPost.setConfig(builderRequestConfig());

            response = httpClient.execute(httpPost);
            resultString = EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (response != null) response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return resultString;
    }

    /**
     * 发送 POST 请求（JSON 格式 application/json）。
     *
     * @param url      请求地址
     * @param paramMap JSON 参数（可为 null）
     * @return 响应体字符串（UTF-8）
     * @throws IOException 网络异常
     */
    public static String doPost4Json(String url, Map<String, String> paramMap) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        String resultString = "";
        try {
            HttpPost httpPost = new HttpPost(url);

            if (paramMap != null) {
                StringEntity entity = new StringEntity(JSONUtil.toJsonStr(paramMap), "utf-8");
                entity.setContentEncoding("utf-8");
                entity.setContentType("application/json");
                httpPost.setEntity(entity);
            }
            httpPost.setConfig(builderRequestConfig());

            response = httpClient.execute(httpPost);
            resultString = EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (Exception e) {
            throw e;
        } finally {
            try {
                if (response != null) response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return resultString;
    }

    private static RequestConfig builderRequestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MSEC)
                .setConnectionRequestTimeout(TIMEOUT_MSEC)
                .setSocketTimeout(TIMEOUT_MSEC)
                .build();
    }
}
