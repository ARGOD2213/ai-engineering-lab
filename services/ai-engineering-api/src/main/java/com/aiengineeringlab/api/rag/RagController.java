package com.aiengineeringlab.api.rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    public final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/ping")
    public String ping() {
        return ragService.ping();
    }

    @PostMapping("/embed")
    public float[] embbed(@RequestBody String text) {
        return ragService.embed(text);
    }
}
