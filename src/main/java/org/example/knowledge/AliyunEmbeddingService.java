package org.example.knowledge;

import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 阿里云 AnalyticDB PostgreSQL Embedding 服务
 * 使用阿里云 TextEmbedding API 将文本转换为向量
 * API 文档: https://api.aliyun.com/api/gpdb/2016-05-03/TextEmbedding
 */
@Slf4j
@Service
public class AliyunEmbeddingService {

    private final AnalyticDBConfig analyticDBConfig;
    private OkHttpClient httpClient;

    public AliyunEmbeddingService(AnalyticDBConfig analyticDBConfig) {
        this.analyticDBConfig = analyticDBConfig;
    }

    /**
     * 初始化 HTTP 客户端
     */
    @PostConstruct
    public void init() {
        if (!analyticDBConfig.hasAliyunCredentials()) {
            log.warn("阿里云认证信息未配置，Embedding 功能将不可用");
            return;
        }

        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        log.info("阿里云 AnalyticDB Embedding 服务初始化成功");
        log.info("区域: {}", analyticDBConfig.getRegionId());
        log.info("实例ID: {}", analyticDBConfig.getDbInstanceId());
        log.info("模型: {}", analyticDBConfig.getEmbeddingModel());
        log.info("向量维度: {}", analyticDBConfig.getEmbeddingDimension());
    }

    /**
     * 将单个文本转换为向量
     *
     * @param text 输入文本
     * @return 向量数组
     */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("输入文本不能为空");
        }

        if (httpClient == null) {
            throw new IllegalStateException("Embedding 服务未初始化，请检查阿里云认证配置");
        }

        try {
            log.debug("调用 Embedding API: text length={}", text.length());

            // 构建请求参数
            JSONObject requestBody = new JSONObject();
            requestBody.set("Input", new JSONArray().set(text));
            requestBody.set("Dimension", analyticDBConfig.getEmbeddingDimension());
            requestBody.set("Model", analyticDBConfig.getEmbeddingModel());
            requestBody.set("RegionId", analyticDBConfig.getRegionId());
            requestBody.set("DBInstanceId", analyticDBConfig.getDbInstanceId());

            // 发送 HTTP 请求
            String responseJson = callAliyunApi(requestBody.toString());
            
            // 解析响应
            JSONObject response = JSONUtil.parseObj(responseJson);
            if (!response.containsKey("Output") || response.getJSONObject("Output") == null) {
                throw new RuntimeException("Embedding API 返回结果格式错误");
            }

            JSONObject output = response.getJSONObject("Output");
            if (!output.containsKey("Embeddings") || output.getJSONArray("Embeddings") == null
                || output.getJSONArray("Embeddings").isEmpty()) {
                throw new RuntimeException("Embedding API 返回结果为空");
            }

            // 获取第一个文本的向量
            JSONArray embeddings = output.getJSONArray("Embeddings");
            JSONObject firstEmbedding = embeddings.getJSONObject(0);
            JSONArray vectorArray = firstEmbedding.getJSONArray("Embedding");

            if (vectorArray == null || vectorArray.isEmpty()) {
                throw new RuntimeException("Embedding 向量为空");
            }

            // 转换为 float 数组
            float[] embedding = new float[vectorArray.size()];
            for (int i = 0; i < vectorArray.size(); i++) {
                embedding[i] = vectorArray.getFloat(i);
            }

            log.debug("Embedding 生成成功: dimension={}", embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("Embedding 生成失败: text={}", text.substring(0, Math.min(50, text.length())), e);
            throw new RuntimeException("Embedding 生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量将文本转换为向量
     *
     * @param texts 输入文本列表
     * @return 向量数组列表
     */
    public List<float[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("输入文本列表不能为空");
        }

        if (httpClient == null) {
            throw new IllegalStateException("Embedding 服务未初始化，请检查阿里云认证配置");
        }

        try {
            log.debug("批量调用 Embedding API: count={}", texts.size());

            // 构建请求参数
            JSONObject requestBody = new JSONObject();
            requestBody.set("Input", new JSONArray(texts));
            requestBody.set("Dimension", analyticDBConfig.getEmbeddingDimension());
            requestBody.set("Model", analyticDBConfig.getEmbeddingModel());
            requestBody.set("RegionId", analyticDBConfig.getRegionId());
            requestBody.set("DBInstanceId", analyticDBConfig.getDbInstanceId());

            // 发送 HTTP 请求
            String responseJson = callAliyunApi(requestBody.toString());
            
            // 解析响应
            JSONObject response = JSONUtil.parseObj(responseJson);
            JSONObject output = response.getJSONObject("Output");
            
            if (output == null || !output.containsKey("Embeddings") 
                || output.getJSONArray("Embeddings") == null) {
                throw new RuntimeException("Embedding API 返回结果格式错误");
            }

            // 转换所有文本的向量
            JSONArray embeddings = output.getJSONArray("Embeddings");
            return embeddings.stream()
                .map(item -> {
                    JSONObject embeddingObj = (JSONObject) item;
                    JSONArray vectorArray = embeddingObj.getJSONArray("Embedding");
                    float[] embedding = new float[vectorArray.size()];
                    for (int i = 0; i < vectorArray.size(); i++) {
                        embedding[i] = vectorArray.getFloat(i);
                    }
                    return embedding;
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("批量 Embedding 生成失败", e);
            throw new RuntimeException("批量 Embedding 生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用阿里云 API
     */
    private String callAliyunApi(String requestBody) throws IOException {
        String endpoint = "https://gpdb." + analyticDBConfig.getRegionId() + ".aliyuncs.com";
        String action = "TextEmbedding";
        String version = "2016-05-03";
        
        // 生成时间戳
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        // 构建签名参数
        Map<String, String> params = new TreeMap<>();
        params.put("Action", action);
        params.put("Version", version);
        params.put("Timestamp", timestamp);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("AccessKeyId", analyticDBConfig.getAccessKeyId());
        params.put("Format", "JSON");

        // 计算签名
        String signature = calculateSignature(params, requestBody, analyticDBConfig.getAccessKeySecret());
        params.put("Signature", signature);

        // 构建查询字符串
        String queryString = params.entrySet().stream()
            .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
            .collect(Collectors.joining("&"));

        // 发送 POST 请求
        RequestBody body = RequestBody.create(
            requestBody, 
            MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
            .url(endpoint + "/?" + queryString)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-acs-action", action)
            .addHeader("x-acs-version", version)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API 请求失败: " + response.code() + " " + response.message());
            }
            
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("API 返回空响应");
            }
            
            return responseBody.string();
        }
    }

    /**
     * 计算签名
     */
    private String calculateSignature(Map<String, String> params, String requestBody, String accessKeySecret) {
        try {
            // 构建规范化请求字符串
            String canonicalizedQueryString = params.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

            // 构建待签名字符串
            String stringToSign = "POST" + "&" + 
                urlEncode("/") + "&" + 
                urlEncode(canonicalizedQueryString);

            // 计算 HMAC-SHA1
            HMac hmac = new HMac(HmacAlgorithm.HmacSHA1, 
                (accessKeySecret + "&").getBytes(StandardCharsets.UTF_8));
            byte[] signData = hmac.digest(stringToSign.getBytes(StandardCharsets.UTF_8));
            
            // Base64 编码
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new RuntimeException("签名计算失败", e);
        }
    }

    /**
     * URL 编码
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException("URL 编码失败", e);
        }
    }

    /**
     * 获取向量维度
     */
    public int getDimension() {
        return analyticDBConfig.getEmbeddingDimension();
    }
}
