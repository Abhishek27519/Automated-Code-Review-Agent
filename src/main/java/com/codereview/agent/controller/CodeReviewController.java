package com.codereview.agent.controller;

import com.codereview.agent.dto.ReviewRequest;
import com.codereview.agent.dto.ReviewResult;
import com.codereview.agent.service.CodeReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class CodeReviewController {

    private final CodeReviewService service;

    public CodeReviewController(CodeReviewService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    public ReviewResult analyze(@Valid @RequestBody ReviewRequest request) {
        return service.review(request);
    }
}