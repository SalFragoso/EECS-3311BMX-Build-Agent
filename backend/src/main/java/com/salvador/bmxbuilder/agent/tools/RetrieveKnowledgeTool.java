package com.salvador.bmxbuilder.agent.tools;

import com.salvador.bmxbuilder.agent.knowledge.KnowledgeDoc;
import com.salvador.bmxbuilder.agent.knowledge.KnowledgeRetriever;
import com.salvador.bmxbuilder.agent.llm.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Lets the agent search the BMX knowledge base for articles to base its answers on (F09). */
@Component
public class RetrieveKnowledgeTool implements AgentTool {

    private final KnowledgeRetriever retriever;

    public RetrieveKnowledgeTool(KnowledgeRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Search the BMX knowledge base (parts standards, sizing, riding styles). "
                        + "Returns matching articles to base answers on.",
                Schemas.object(Schemas.props("query", Schemas.string("What to look up, e.g. 'spanish bottom bracket'")),
                        List.of("query")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String query = ToolArgs.requiredString(args, "query");
        List<KnowledgeDoc> docs = retriever.retrieve(query, 3);
        if (docs.isEmpty()) {
            return ToolResult.ok("No knowledge base articles matched '" + query + "'.");
        }
        return ToolResult.ok(docs.stream()
                .map(d -> "[" + d.title() + "]\n" + d.text())
                .collect(Collectors.joining("\n\n")));
    }
}
