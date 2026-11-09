package com.salvador.bmxbuilder.agent.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keyword search over a small built-in BMX knowledge base (the retrieval part of F09).
 * Scoring: a query word that matches a doc's keywords counts 3, its title 2, its text 1.
 * Plain keyword matching keeps it deterministic and easy to test; no embeddings needed at this size.
 */
@Component
public class KnowledgeRetriever {

    static final int MIN_SCORE = 2;

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "what", "whats", "which", "how", "should", "can", "does", "with", "are",
            "you", "your", "get", "use", "one", "this", "that", "from", "have", "has", "need", "want",
            "is", "a", "an", "of", "to", "in", "on", "my", "me", "it", "or", "be", "do", "i", "vs", "bmx", "bike");

    private final List<KnowledgeDoc> docs;

    public KnowledgeRetriever() {
        this(BmxKnowledgeBase.articles());
    }

    public KnowledgeRetriever(List<KnowledgeDoc> docs) {
        this.docs = List.copyOf(docs);
    }

    /** The best k matching docs, best first. Empty if nothing matches well enough. */
    public List<KnowledgeDoc> retrieve(String query, int k) {
        Set<String> queryWords = words(query);
        if (queryWords.isEmpty()) {
            return List.of();
        }
        record Scored(KnowledgeDoc doc, int score) { }
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            int score = score(queryWords, doc);
            if (score >= MIN_SCORE) {
                scored.add(new Scored(doc, score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(k)
                .map(Scored::doc)
                .toList();
    }

    private int score(Set<String> queryWords, KnowledgeDoc doc) {
        Set<String> keywordWords = words(String.join(" ", doc.keywords()));
        Set<String> titleWords = words(doc.title());
        Set<String> textWords = words(doc.text());
        int score = 0;
        for (String w : queryWords) {
            if (keywordWords.contains(w)) score += 3;
            if (titleWords.contains(w)) score += 2;
            if (textWords.contains(w)) score += 1;
        }
        return score;
    }

    /** Lowercase words without stopwords, with a plural "s" trimmed so "brakes" matches "brake". */
    static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) {
                continue;
            }
            String w = (raw.length() > 3 && raw.endsWith("s") && !raw.endsWith("ss"))
                    ? raw.substring(0, raw.length() - 1)
                    : raw;
            out.add(w);
        }
        return out;
    }
}
