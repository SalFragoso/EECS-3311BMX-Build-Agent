package com.salvador.bmxbuilder.web;

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
}
