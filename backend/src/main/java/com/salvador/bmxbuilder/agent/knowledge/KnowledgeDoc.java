package com.salvador.bmxbuilder.agent.knowledge;

import java.util.List;

/**
 * One short article in the built-in BMX knowledge base.
 * keywords are extra search terms (including abbreviations like "bb") that boost matching.
 */
public record KnowledgeDoc(String id, String title, String text, List<String> keywords) {
}
