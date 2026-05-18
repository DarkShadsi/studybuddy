package com.studyapp.service;

import java.util.List;

public class ImportPreview {
    private final List<CardJson> cards;
    private final String deckName;
    private final String description;
    private final String exportedAt;

    public ImportPreview(List<CardJson> cards, String deckName, String description, String exportedAt) {
        this.cards = cards;
        this.deckName = deckName;
        this.description = description;
        this.exportedAt = exportedAt;
    }

    public List<CardJson> getCards() {
        return cards;
    }

    public String getDeckName() {
        return deckName;
    }

    public String getDescription() {
        return description;
    }

    public String getExportedAt() {
        return exportedAt;
    }

    public boolean hasDeckMetadata() {
        return (deckName != null && !deckName.isBlank())
                || (description != null && !description.isBlank())
                || (exportedAt != null && !exportedAt.isBlank());
    }
}
