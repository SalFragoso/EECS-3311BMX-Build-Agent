package com.salvador.bmxbuilder.web;

import com.salvador.bmxbuilder.domain.RidingStyle;
import com.salvador.bmxbuilder.recommend.RiderProfile;
import com.salvador.bmxbuilder.recommend.StyleRecommendation;
import com.salvador.bmxbuilder.recommend.UpgradeAdvisor;
import com.salvador.bmxbuilder.service.AgentAnswer;
import com.salvador.bmxbuilder.service.BuildService;
import com.salvador.bmxbuilder.service.PlanResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

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

    // POST /api/agent/recommend-style?heightCm=175&experience=BEGINNER&spots=street,skatepark&goals=grind rails&wantsBrakes=false
    @PostMapping("/recommend-style")
    public StyleRecommendation recommendStyle(@RequestParam("heightCm") int heightCm,
                                              @RequestParam(name = "experience", required = false) String experience,
                                              @RequestParam(name = "spots", required = false) List<String> spots,
                                              @RequestParam(name = "goals", required = false) String goals,
                                              @RequestParam(name = "budget", required = false) BigDecimal budget,
                                              @RequestParam(name = "wantsBrakes", defaultValue = "false") boolean wantsBrakes) {
        return service.recommendStyle(new RiderProfile(heightCm, experience, spots, goals, budget, wantsBrakes));
    }

    // POST /api/agent/upgrade?buildId=3&budget=400&style=STREET
    // POST /api/agent/upgrade?description=stock complete with plastic pedals&budget=400  (F11)
    @PostMapping("/upgrade")
    public UpgradeAdvisor.Plan upgrade(@RequestParam(name = "buildId", required = false) Long buildId,
                                       @RequestParam(name = "description", required = false) String description,
                                       @RequestParam("budget") BigDecimal budget,
                                       @RequestParam(name = "style", required = false) RidingStyle style) {
        return service.planUpgrade(buildId, description, budget, style);
    }
}
