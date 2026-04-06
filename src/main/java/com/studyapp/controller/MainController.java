package com.studyapp.controller;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.studyapp.dao.FlashcardDAO;
import com.studyapp.dao.impl.CardReviewDAOImpl;
import com.studyapp.dao.impl.DeckDAOImpl;
import com.studyapp.dao.impl.FlashcardDAOImpl;
import com.studyapp.dao.impl.StudySessionDAOImpl;
import com.studyapp.db.DatabaseConnection;
import com.studyapp.model.CardReview;
import com.studyapp.model.Deck;
import com.studyapp.model.Flashcard;
import com.studyapp.model.StudySession;

//HANDLES ALL OPERATIONS THAT CONNECTS BACKEND WITH FRONTEND
//INCLUDES:
//CRUD OPERATIONS
//DATA/FILE HANDLING
//AUTHENTICATION
//DATA VALIDATION

public class MainController {
    private DeckDAOImpl deckDaoImpl = new DeckDAOImpl();
    private CredentialHandler cHandler = new CredentialHandler();
    private FlashcardDAOImpl flashcardDAOImpl = new FlashcardDAOImpl();
    private StudySessionDAOImpl studySessionDAOImpl = new StudySessionDAOImpl();
    private CardReviewDAOImpl cardReviewDAOImpl = new CardReviewDAOImpl();

    private List<Deck> decks = new ArrayList<>();
    private List<Flashcard> flashcards = new ArrayList<>();
    private List<StudySession> studySessions = new ArrayList<>();
    private List<CardReview> cardReviews = new ArrayList<>();

    private List<Deck> addedDecks    = new ArrayList<>();
    private Map<Integer, Deck> modifiedDecks = new HashMap<>();
    private List<Integer> deletedDecks  = new ArrayList<>();
    private List<Flashcard> addedFlashcards = new ArrayList<>();
    private Map<Integer, Flashcard> modifiedFlashcards = new HashMap<>();
    private List<Integer> deletedFlashcards  = new ArrayList<>();

    private int lastDeckID = 999;
    private int lastCardID = 999;

    // --------- AUTHENTICATION --------------
    public boolean tryAutoLogin() {
        if (!cHandler.checkForCred()) return false;
        if (!cHandler.readAndValidate()) return false;
        try {
            loadData();
            return true;
        } catch (CustomException e) {
            return false;
        }
    }

    public void login(String username, String password) throws CustomException {
        if (!DatabaseConnection.authenticate(username, password)) {
            throw new CustomException("Invalid credentials.");
        }
        cHandler.write(username, password);
        DatabaseConnection.setCredentials(username, password);
        loadData();
    }

    //------------- DMLs --------------------
    //-----DECK--------
    public void createDeck(String deckName, String description) throws CustomException{
        Deck deck = new Deck(++lastDeckID, deckName, description, LocalDateTime.now());
        //TODO: Validate constraints first before adding
        decks.add(deck);
        addedDecks.add(deck);
    }

    public void updateDeck(int deckID, String deckName, String description) throws CustomException{
        try{
            Deck deck = new Deck(deckID, deckName, description, LocalDateTime.now());
            deckDaoImpl.update(deck);
        }catch(SQLException e){
            throw new CustomException("Error updating deck.");
        }
    }

    public void deleteDeck(int deckID) throws CustomException{
        Deck existing = decks.stream()
                .filter(i -> i.getDeckID() == deckID)
                .findFirst().orElse(null);
        if (existing == null) {
            throw new CustomException("No record matched. No row was deleted.");
        }
        decks.remove(existing);

        if (addedDecks.contains(existing)) {
            addedDecks.remove(existing);
        } else {
            modifiedDecks.remove(deckID);
            deletedDecks.add(deckID);
        }

        //DELETE ALL CARDS IN THIS DECK
        for(Flashcard flashcard: getFlashcardsByDeck(deckID)){
            deleteFlashcard(flashcard.getCardID());
        }
        //MUST ALSO DELETE ALL SESSIONS ASSOCIATED IN THIS DECK
    }

    public Deck findDeck(int deckID){
        return deckDaoImpl.findByID(deckID);
    }

    public List<Deck> allDecks(){
        return new ArrayList<>(decks);
    }

    public void update(Deck deck) throws CustomException {
        Deck existing = decks.stream()
                .filter(i -> i.getDeckID() == deck.getDeckID())
                .findFirst().orElse(null);
        if (existing == null) {
            throw new CustomException("Deck not found.");
        }

        decks.remove(existing);
        decks.add(deck);

        if (addedDecks.contains(existing)) {
            addedDecks.remove(existing);
            addedDecks.add(deck);
        } else {
            modifiedDecks.put(deck.getDeckID(), deck);
        }
    }

    //-----FLASHCARDS------
    public List<Flashcard> allFlashcards(){
        return new ArrayList<>(flashcards);
    }

    public List<Flashcard> getFlashcardsByDeck(int deckID){
        return flashcards.stream()
                .filter(card -> card.getDeck().getDeckID() == deckID)
                .toList();
    }

    public void createFlashcard(int deckID, String question, String answer, String difficulty) throws CustomException{
        //CHECK IF DECK EXISTS
        Deck deck = decks.stream()
                .filter(i -> i.getDeckID() == deckID)
                .findFirst().orElse(null);
        if (deck == null) {
            throw new CustomException("Deck does not exist.");
        }

        Flashcard flashcard = new Flashcard(++lastCardID, deck, question, answer, difficulty, LocalDateTime.now());
        //TODO: validate card constraints
        flashcards.add(flashcard);
        addedFlashcards.add(flashcard);
    }

    public void updateFlashcard(Flashcard flashcard) throws CustomException {
        Flashcard existing = flashcards.stream()
                .filter(i -> i.getCardID() == flashcard.getCardID())
                .findFirst().orElse(null);
        if (existing == null) {
            throw new CustomException("Flashcard not found.");
        }

        flashcards.remove(existing);
        flashcards.add(flashcard);

        if (addedFlashcards.contains(existing)) {
            addedFlashcards.remove(existing);
            addedFlashcards.add(flashcard);
        } else {
            modifiedFlashcards.put(flashcard.getCardID(), flashcard);
        }
    }

    public void deleteFlashcard(int flashcardID) throws CustomException {
        Flashcard existing = flashcards.stream()
                .filter(i -> i.getCardID() == flashcardID)
                .findFirst().orElse(null);
        if (existing == null) {
            throw new CustomException("No record matched. No row was deleted.");
        }
        flashcards.remove(existing);

        if (addedFlashcards.contains(existing)) {
            addedFlashcards.remove(existing);
        } else {
            modifiedFlashcards.remove(flashcardID);
            deletedFlashcards.add(flashcardID);
        }
    }

    //----------------- DATA -----------------------
    public void loadData() throws CustomException{
        try{
            decks = deckDaoImpl.getAllDecks();
            lastDeckID = deckDaoImpl.getLastID();
            flashcards = flashcardDAOImpl.getAllFlashcards();
            lastCardID = flashcardDAOImpl.getLastID();
            studySessions = studySessionDAOImpl.getAllSessions();
            cardReviews = cardReviewDAOImpl.getAllReviews();
        }catch(Exception e){
            throw new CustomException("Failed to Load Data");
        }
    }

}
