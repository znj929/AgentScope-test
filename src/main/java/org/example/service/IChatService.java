package org.example.service;

import reactor.core.publisher.Flux;

public interface IChatService {
    Flux<String> chatStream(String userId, String sessionId, String datasetId, String content);
}
