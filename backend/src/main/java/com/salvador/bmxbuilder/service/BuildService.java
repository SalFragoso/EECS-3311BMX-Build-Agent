package com.salvador.bmxbuilder.service;

import com.salvador.bmxbuilder.agent.AgentOrchestrator;
import com.salvador.bmxbuilder.agent.AgentResult;
import com.salvador.bmxbuilder.agent.AgentTask;
import com.salvador.bmxbuilder.agent.Prompts;
import com.salvador.bmxbuilder.agent.knowledge.KnowledgeDoc;
import com.salvador.bmxbuilder.agent.knowledge.KnowledgeRetriever;
import com.salvador.bmxbuilder.agent.tools.BuildSummary;
import com.salvador.bmxbuilder.agent.llm.LLMException;
import com.salvador.bmxbuilder.data.BuildRepository;
import com.salvador.bmxbuilder.data.PartRepository;
import com.salvador.bmxbuilder.domain.BikeBuild;
import com.salvador.bmxbuilder.domain.Part;
import com.salvador.bmxbuilder.domain.PartCategory;
import com.salvador.bmxbuilder.domain.PartQuery;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityChecker;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityMonitor;
import com.salvador.bmxbuilder.domain.compatibility.CompatibilityReport;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facade pattern: the single entry point for the REST controllers and the CLI.
 * They never touch repositories or domain logic directly.
 */
@Service
public class BuildService {

    private final PartRepository parts;
    private final BuildRepository builds;
    private final CompatibilityChecker checker;
    private final AgentOrchestrator agent;
    private final KnowledgeRetriever knowledge;

    public BuildService(PartRepository parts, BuildRepository builds, CompatibilityChecker checker,
                        AgentOrchestrator agent, KnowledgeRetriever knowledge) {
        this.parts = parts;
        this.builds = builds;
        this.checker = checker;
        this.agent = agent;
        this.knowledge = knowledge;
    }

    // ---------- F03: catalog search ----------

    public List<Part> searchParts(PartQuery query) {
        if (query.minPrice() != null && query.maxPrice() != null
                && query.minPrice().compareTo(query.maxPrice()) > 0) {
            throw new IllegalArgumentException("Min price must be below max price.");
        }
        return parts.search(query);
    }

    public Part getPart(Long partId) {
        return parts.findById(partId)
                .orElseThrow(() -> new NotFoundException("Part " + partId + " not found"));
    }

    // ---------- F04: build configurator ----------

    public BuildView createBuild(String name) {
        String cleanName = (name == null || name.isBlank()) ? "Untitled build" : name.trim();
        BikeBuild saved = builds.save(new BikeBuild(cleanName));
        return BuildView.of(saved, checker.check(saved));
    }

    public BuildView viewBuild(Long buildId) {
        BikeBuild build = loadBuild(buildId);
        return BuildView.of(build, checker.check(build));
    }

    public List<BuildView> listBuilds() {
        return builds.findAll().stream()
                .map(b -> BuildView.of(b, checker.check(b)))
                .toList();
    }

    /**
     * Puts a catalog part into a slot. A part that doesn't fit is still allowed (F04 says flag it,
     * don't block it), and the returned view carries the compatibility report.
     */
    public BuildView selectPart(Long buildId, PartCategory slot, Long partId) {
        BikeBuild build = loadBuild(buildId);
        Part part = getPart(partId);

        // Observer pattern in action: the monitor re-checks compatibility when the build changes
        CompatibilityMonitor monitor = new CompatibilityMonitor(checker);
        build.addListener(monitor);
        build.setPart(slot, part);

        BikeBuild saved = builds.save(build);
        return BuildView.of(saved, reportFrom(monitor, saved));
    }

    public BuildView removePart(Long buildId, PartCategory slot) {
        BikeBuild build = loadBuild(buildId);

        CompatibilityMonitor monitor = new CompatibilityMonitor(checker);
        build.addListener(monitor);
        build.removePart(slot);

        BikeBuild saved = builds.save(build);
        return BuildView.of(saved, reportFrom(monitor, saved));
    }

    // ---------- F05: compatibility ----------

    public CompatibilityReport checkCompatibility(Long buildId) {
        return checker.check(loadBuild(buildId));
    }

    /** Catalog parts for a slot that would fit the build as it is now. */
    public List<Part> compatibleParts(Long buildId, PartCategory slot) {
        BikeBuild build = loadBuild(buildId);
        return parts.findByCategory(slot).stream()
                .filter(p -> checker.isCompatible(build, p))
                .toList();
    }

    // ---------- F02: AI build planner ----------

    static final int PLANNER_MAX_STEPS = 25;
    static final Set<String> PLANNER_TOOLS = Set.of("search_catalog", "add_part_to_build", "check_compatibility");

    /**
     * Creates a build and lets the agent fill it from a plain-language request, e.g.
     * "street bike under $2000, no brakes". The agent only changes the build through tools, and the
     * finished build is re-checked here by the deterministic checker, so the AI can't skip the rules.
     */
    public PlanResult planBuild(String request, String name) {
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("Describe the bike you want, e.g. 'street bike under $2000, no brakes'");
        }
        String buildName = (name == null || name.isBlank()) ? "AI build" : name.trim();
        BikeBuild build = builds.save(new BikeBuild(buildName));
        Long buildId = build.getId();

        AgentTask task = new AgentTask("plan-" + buildId, Prompts.planner(buildId == null ? 0 : buildId),
                request.trim(), null, PLANNER_TOOLS, PLANNER_MAX_STEPS);
        try {
            AgentResult result = agent.run(task);
            BikeBuild filled = loadBuild(buildId);   // the tools saved their changes; reload to see them
            return new PlanResult(BuildView.of(filled, checker.check(filled)), result.text(), result.steps(),
                    result.finished(), true, result.tokensUsed());
        } catch (LLMException e) {
            // F02 fallback: keep whatever the agent added before the AI failed (e.g. a rate limit
            // partway through), so the rider can finish the build manually (F04)
            BikeBuild current = loadBuild(buildId);
            String message = current.getSlots().isEmpty()
                    ? "The AI planner is unavailable right now (" + e.getMessage() + "). Build " + buildId
                      + " was created empty, so you can fill it manually instead."
                    : "The AI planner stopped partway (" + e.getMessage() + "). Build " + buildId
                      + " has the parts it picked so far, so you can finish it manually.";
            return new PlanResult(BuildView.of(current, checker.check(current)), message, List.of(), false, false, 0);
        }
    }

    // ---------- F09: BMX Q&A assistant ----------

    static final int QA_MAX_STEPS = 6;
    static final Set<String> QA_TOOLS = Set.of("search_knowledge", "search_catalog");

    /**
     * Answers a BMX question. Matching knowledge base articles are found first and sent with the
     * question; the agent can search for more. Follow-ups in the same conversation see earlier turns.
     *
     * @param conversationId groups follow-up questions (e.g. "cli" or one per browser tab)
     * @param buildId        optional: include this build so the answer can refer to it
     */
    public AgentAnswer askQuestion(String conversationId, String question, Long buildId) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Ask a BMX question, e.g. 'mid or Spanish bottom bracket?'");
        }
        String convo = (conversationId == null || conversationId.isBlank()) ? "default" : conversationId;
        List<KnowledgeDoc> docs = knowledge.retrieve(question, 3);
        List<String> sources = docs.stream().map(KnowledgeDoc::title).toList();

        StringBuilder context = new StringBuilder();
        if (docs.isEmpty()) {
            context.append("Knowledge base: no articles matched this question.");
        } else {
            context.append("Knowledge base passages:");
            for (KnowledgeDoc d : docs) {
                context.append("\n[").append(d.title()).append("]\n").append(d.text());
            }
        }
        if (buildId != null) {
            BikeBuild build = loadBuild(buildId);
            context.append("\n\nThe rider's current build:\n").append(BuildSummary.build(build, checker.check(build)));
        }

        AgentTask task = new AgentTask(convo, Prompts.questionAnswering(), question.trim(), context.toString(),
                QA_TOOLS, QA_MAX_STEPS);
        try {
            AgentResult result = agent.run(task);
            return new AgentAnswer(result.text(), sources, result.steps(), true, result.tokensUsed());
        } catch (LLMException e) {
            // F09 fallback: show the matching articles directly
            String answer = docs.isEmpty()
                    ? "The AI assistant is unavailable right now (" + e.getMessage() + ") and no articles matched."
                    : "The AI assistant is unavailable right now, but these articles match your question:\n\n"
                      + docs.stream().map(d -> d.title() + ": " + d.text()).collect(Collectors.joining("\n\n"));
            return new AgentAnswer(answer, sources, List.of(), false, 0);
        }
    }

    // ---------- helpers ----------

    private BikeBuild loadBuild(Long buildId) {
        return builds.findById(buildId)
                .orElseThrow(() -> new NotFoundException("Build " + buildId + " not found"));
    }

    /** If nothing changed (e.g. removing an empty slot), the monitor never ran, so check directly. */
    private CompatibilityReport reportFrom(CompatibilityMonitor monitor, BikeBuild build) {
        CompatibilityReport report = monitor.latestReport();
        return (report != null) ? report : checker.check(build);
    }
}
