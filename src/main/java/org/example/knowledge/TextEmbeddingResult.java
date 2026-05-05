package org.example.knowledge;

import com.aliyun.tea.TeaModel;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * 阿里云 TextEmbedding API 响应模型
 */
@Data
public class TextEmbeddingResult {
    
    /**
     * 文本token数量
     */
    @SerializedName("TextTokens")
    private Integer textTokens;
    
    /**
     * 结果集
     */
    @SerializedName("Results")
    private Results results;
    
    @Data
    public static class Results {
        /**
         * 结果列表
         */
        @SerializedName("Results")
        private List<ResultItem> results;
    }
    
    @Data
    public static class ResultItem {
        /**
         * Embedding对象
         */
        @SerializedName("Embedding")
        private EmbeddingData embedding;
        
        /**
         * 索引
         */
        @SerializedName("Index")
        private Integer index;
    }
    
    @Data
    public static class EmbeddingData {
        /**
         * 向量值列表
         */
        @SerializedName("Embedding")
        private List<Float> embedding;
    }
}
