package org.example.knowledge;

import cn.hutool.http.HttpStatus;
import com.aliyun.gpdb20160503.Client;
import com.aliyun.gpdb20160503.models.TextEmbeddingResponse;
import com.aliyun.gpdb20160503.models.TextEmbeddingResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 阿里云 AnalyticDB PostgreSQL Embedding 服务
 * 使用阿里云 TextEmbedding API 将文本转换为向量
 * API 文档: https://api.aliyun.com/api/gpdb/2016-05-03/TextEmbedding
 */
@Slf4j
@Service
public class AliyunEmbeddingService {

    private final AnalyticDBConfig analyticDBConfig;
    private Client client;
    private Gson gson;

    public AliyunEmbeddingService(AnalyticDBConfig analyticDBConfig) {
        this.analyticDBConfig = analyticDBConfig;
    }

    /**
     * 初始化 Client
     */
    @PostConstruct
    public void init() throws Exception {
        // 工程代码建议使用更安全的无AK方式，凭据配置方式请参见：https://help.aliyun.com/document_detail/378657.html。
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setCredential(credential);
        // Endpoint 请参考 https://api.aliyun.com/product/gpdb
        config.endpoint = analyticDBConfig.getEndpoint();
        config.accessKeyId = analyticDBConfig.getAccessKeyId()/* "LTAI5t9XGSA5tMj4vXo4Cqeq"*/;
        // 您的AccessKey Secret
        config.accessKeySecret = analyticDBConfig.getAccessKeySecret()/* "E4Jo4Cvg7s0VIqRy0Zo8woMb5PJhoj"*/;
        // region信息
        config.regionId = analyticDBConfig.getRegionId();
        client = new Client(config);
        gson = new Gson();
    }

    /**
     * 将文本转换为向量
     *
     * @param input 输入文本
     * @return 向量数组
     */
    public float[] embeddingText(String input) throws Exception {
        // 输入验证
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("输入文本不能为空");
        }
        
        com.aliyun.gpdb20160503.models.TextEmbeddingRequest textEmbeddingRequest = new com.aliyun.gpdb20160503.models.TextEmbeddingRequest()
                .setRegionId(analyticDBConfig.getRegionId())
                .setInput(java.util.Arrays.asList(input))
                .setModel(analyticDBConfig.getEmbeddingModel())
                .setDimension(analyticDBConfig.getEmbeddingDimension())
                .setDBInstanceId(analyticDBConfig.getDbInstanceId());
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            // 复制代码运行请自行打印 API 的返回值
            TextEmbeddingResponse textEmbeddingResponse = client.textEmbeddingWithOptions(textEmbeddingRequest, runtime);

            log.debug("Embedding response: {}", textEmbeddingResponse.getBody().toString());
            
            // 解析响应结果
            if(textEmbeddingResponse.getStatusCode() == HttpStatus.HTTP_OK){
                TextEmbeddingResponseBody.TextEmbeddingResponseBodyResults results = textEmbeddingResponse.getBody().getResults();
                
                if (results == null || results.getResults() == null || results.getResults().isEmpty()) {
                    throw new RuntimeException("Embedding结果为空");
                }
                
                List<Double> embeddingList = results.getResults().get(0).getEmbedding().getEmbedding();
                if (embeddingList == null || embeddingList.isEmpty()) {
                    throw new RuntimeException("Embedding向量数据为空");
                }
                
                // 将List<Double>转换为float[]
                float[] result = new float[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    result[i] = embeddingList.get(i).floatValue();
                }
                return result;
            } else {
                throw new RuntimeException("Embedding API调用失败，HTTP状态码: " + textEmbeddingResponse.getStatusCode());
            }
            
        } catch (TeaException error) {
            // 此处仅做打印展示，请谨慎对待异常处理，在工程项目中切勿直接忽略异常。
            // 错误 message
            log.error("Embedding API调用失败: {}", error.getMessage());
            // 诊断地址
            log.error("诊断地址: {}", error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
            throw error;
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 此处仅做打印展示，请谨慎对待异常处理，在工程项目中切勿直接忽略异常。
            // 错误 message
            log.error("Embedding API调用失败: {}", error.getMessage());
            // 诊断地址
            log.error("诊断地址: {}", error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
            throw error;
        }
    }


    /**
     * 获取向量维度
     */
    public int getDimension() {
        return analyticDBConfig.getEmbeddingDimension();
    }
}
