package com.studyapp.service;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.studyapp.controller.CustomException;
import com.studyapp.model.Deck;
import com.studyapp.model.Flashcard;

public class JsonImportExportService {

    // Shared Gson instance configured for pretty-printed output
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Exports a deck's flashcards to a JSON file as a flat card array.
     * Each entry has {@code question}, {@code answer}, and {@code difficulty} fields.
     */
    public void exportDeckToFile(Deck deck, List<Flashcard> cards, File file) throws CustomException {
        List<CardJson> cardJsonList = new ArrayList<>();
        for (Flashcard card : cards) {
            cardJsonList.add(new CardJson(
                    card.getQuestion(),
                    card.getAnswer(),
                    card.getDifficulty()));
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(cardJsonList, writer);
        } catch (IOException e) {
            throw new CustomException("Failed to write export file: " + e.getMessage());
        }
    }

    /**
     * Parses a JSON file and returns a flat list of card candidates for UI preview.
     * Accepts either a JSON array of card objects, or a deck export object with a
     * {@code cards} array.
     * Difficulty is normalised to "Easy", "Medium", or "Hard" if recognised; null otherwise.
     */
    public List<CardJson> previewCards(File file) throws CustomException {
        return previewImport(file).getCards();
    }

    /**
     * Parses a JSON file and returns card candidates plus optional source deck metadata.
     * Supported shapes:
     * {@code [{question, answer, difficulty}, ...]} and
     * {@code {deck_name, description, exported_at, cards:[...]}}.
     */
    public ImportPreview previewImport(File file) throws CustomException {
        List<CardJson> result = new ArrayList<>();
        String deckName = null;
        String description = null;
        String exportedAt = null;

        try (FileReader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);

            JsonElement cardsElement;
            if (root.isJsonArray()) {
                cardsElement = root;
            } else if (root.isJsonObject()) {
                JsonObject deckObject = root.getAsJsonObject();
                cardsElement = deckObject.get("cards");
                if (cardsElement == null || !cardsElement.isJsonArray()) {
                    throw new CustomException("Invalid JSON: expected a card array or a deck object with a cards array.");
                }
                deckName = getOptionalString(deckObject, "deck_name");
                description = getOptionalString(deckObject, "description");
                exportedAt = getOptionalString(deckObject, "exported_at");
            } else {
                throw new CustomException("Invalid JSON: expected a card array or a deck object with a cards array.");
            }

            CardJson[] arr = GSON.fromJson(cardsElement, CardJson[].class);
            for (CardJson c : arr) {
                if (c == null) continue;
                if (c.getQuestion() == null || c.getQuestion().isBlank()) continue;
                if (c.getAnswer()   == null || c.getAnswer().isBlank())   continue;
                result.add(new CardJson(
                    c.getQuestion().trim(),
                    c.getAnswer().trim(),
                    previewDifficulty(c.getDifficulty())
                ));
            }
        } catch (JsonParseException e) {
            throw new CustomException("Malformed JSON file: " + e.getMessage());
        } catch (IOException e) {
            throw new CustomException("Could not read file: " + e.getMessage());
        }
        return new ImportPreview(result, deckName, description, exportedAt);
    }

    private String getOptionalString(JsonObject object, String fieldName) {
        JsonElement value = object.get(fieldName);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive()) return null;
        return value.getAsString();
    }

    /** Returns "Easy", "Medium", or "Hard" if recognised; null otherwise. */
    private String previewDifficulty(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        if (t.equalsIgnoreCase("Easy"))   return "Easy";
        if (t.equalsIgnoreCase("Medium")) return "Medium";
        if (t.equalsIgnoreCase("Hard"))   return "Hard";
        return null;
    }
}
