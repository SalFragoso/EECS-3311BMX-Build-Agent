package com.salvador.bmxbuilder.agent.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeRetrieverTest {

    private final KnowledgeRetriever retriever = new KnowledgeRetriever();

    @Test
    void bottomBracketQuestionFindsTheBbArticleFirst() {
        List<KnowledgeDoc> docs = retriever.retrieve("mid or spanish bottom bracket?", 3);

        assertFalse(docs.isEmpty());
        assertEquals("kb-bottom-brackets", docs.get(0).id());
    }

    @Test
    void abbreviationsMatchThroughKeywords() {
        assertEquals("kb-bottom-brackets", retriever.retrieve("which bb fits", 1).get(0).id());
    }

    @Test
    void pluralsMatchSingulars() {
        assertEquals("kb-brakes", retriever.retrieve("brakes", 1).get(0).id());
    }

    @Test
    void freecoasterQuestion() {
        assertEquals("kb-freecoaster-cassette", retriever.retrieve("what's a freecoaster?", 1).get(0).id());
    }

    @Test
    void offTopicQuestionFindsNothing() {
        assertTrue(retriever.retrieve("best pizza in Toronto", 3).isEmpty());
    }

    @Test
    void onlyStopwordsFindsNothing() {
        assertTrue(retriever.retrieve("what is the", 3).isEmpty());
    }

    @Test
    void respectsTheLimit() {
        assertTrue(retriever.retrieve("frame fork hub axle tire brake chain", 2).size() <= 2);
    }

    @Test
    void customArticlesCanBeSearched() {
        KnowledgeRetriever custom = new KnowledgeRetriever(List.of(
                new KnowledgeDoc("x", "Pegs", "Pegs let you grind rails.", List.of("peg", "grind"))));

        assertEquals("x", custom.retrieve("which pegs for grinding", 1).get(0).id());
    }
}
