package com.salvador.bmxbuilder.web;

import com.salvador.bmxbuilder.service.AgentAnswer;
import com.salvador.bmxbuilder.service.BuildService;
import com.salvador.bmxbuilder.service.PlanResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the AI agent features. */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final BuildService service;

    public AgentController(BuildService service) {
        this.service = service;
    }

    // POST /api/agent/plan?request=street bike under $2000, no brakes&name=My street build
    @PostMapping("/plan")
    public PlanResult plan(@RequestParam("request") String request,
                           @RequestParam(name = "name", required = false) String name) {
        return service.planBuild(request, name);
    }

    // POST /api/agent/ask?question=mid or spanish bottom bracket?&conversationId=tab-1&buildId=3
    @PostMapping("/ask")
    public AgentAnswer ask(@RequestParam("question") String question,
                           @RequestParam(name = "conversationId", required = false) String conversationId,
                           @RequestParam(name = "buildId", required = false) Long buildId) {
        return service.askQuestion(conversationId == null ? "web" : conversationId, question, buildId);
    }
}
