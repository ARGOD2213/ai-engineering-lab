package com.aiengineeringlab.api.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final EmbeddingModel embeddingModel;

    public RagService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String ping() {
        return "@Service annotation and i will choose constructor injection as beacause i can avail services from different services in future so that directly inject them or more like i can specify or limit the service beans on this controller";
    }

    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

}
