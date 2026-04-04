package com.studyapp.view;

import java.util.List;
import java.util.Scanner;

import com.studyapp.controller.CustomException;
import com.studyapp.controller.MainController;
import com.studyapp.model.Deck;
import com.studyapp.model.Flashcard;

//MAINLY FOR TESTING OUT IMPLEMENTED METHODS ONLY WITHOUT WORRYING GUI
public class CLIView {
    private MainController mc;
    private Scanner scanner = new Scanner(System.in);

    private static final String BAR =
            "__________________________________________________________";

    public CLIView(MainController mc){
        this.mc = mc;
    }

    public void start(){
        if(mc.tryAutoLogin()){
            mainMenu();
        }else{
            loginAndStart();
        }
    }

    public void loginAndStart() {
        System.out.println("\n--- CONNECT YOUR DATABASE ---");
        while (true) {
            System.out.print("MySQL username: ");
            String username = readLine();
            System.out.print("MySQL password: ");
            String password = readLine();
            try {
                mc.login(username, password);
                System.out.println("Login successful. Credentials saved.\n");
                break;
            } catch (CustomException e) {
                System.out.println(e.getMessage() + "\n");
            }
        }
        mainMenu();
    }

    public void mainMenu(){
        while (true) {
            printMainMenu();
            try {
                int choice = readInt();
                switch (choice) {
                    case 1 -> { manageDecks();}
                    case 2 -> { allCards(); askNextAction(); }
                    case 4 -> { System.exit(0); }
                    default -> System.out.println("Invalid choice.\n");
                }
            } catch (Exception e) {
                System.out.println("Invalid input.\n");
                scanner.nextLine();
            }
        }
    }

    void manageDecks(){
        System.out.println(BAR + "\n--- MANAGE DECKS ---");
        List<Deck> decks = mc.allDecks();

        if (decks.isEmpty()) {
            System.out.println("No decks available.\n");
            return;
        }

        System.out.printf("%-6s %-20s \n", "ID", "NAME");
        for (Deck deck : decks) {
            System.out.printf("%-6d %-20s \n", deck.getDeckID(), deck.getName());
        }

        while (true) {
            System.out.println("\nEnter deck ID to manage, or 0 to return to main menu:");
            int choice = readInt();
            if (choice == 0) {
                return;
            }

            Deck selectedDeck = null;
            for (Deck deck : decks) {
                if (deck.getDeckID() == choice) {
                    selectedDeck = deck;
                    break;
                }
            }

            if (selectedDeck != null) {
                deckDescription(selectedDeck);
                return;
            } else {
                System.out.println("Deck ID not found. Please try again.");
            }
        }
    }

    void deckDescription(Deck deck){
        System.out.println("\n --- " + deck.getName() + " ---\n");
        System.out.println("Deck ID: " + deck.getDeckID());
        System.out.println("Cards: " + mc.getFlashcardsByDeck(deck.getDeckID()).size());
        System.out.println("Description: " + deck.getDescription());
        System.out.println("Created at: " + deck.getCreatedAt());
    }

    void allCards(){
        System.out.println(BAR + "\n--- ALL CARDS ---");
        List<Flashcard> allCards = mc.allFlashcards();

        if (allCards.isEmpty()) {
            System.out.println("No flashcards available.\n");
            return;
        }

        System.out.printf("%-6s %-30s  %-12s\n", "ID", "QUESTION", "DECK ID");
        for (Flashcard card : allCards) {
            String question = card.getQuestion() == null ? "" : card.getQuestion();
            int deckId = card.getDeck() != null ? card.getDeck().getDeckID() : 0;
            System.out.printf("%-6d %-30.20s   %-12d\n", card.getCardID(), question, deckId);
        }

        while (true) {
            System.out.println("\nEnter card ID to view/manage, or 0 to return to main menu:");
            int choice = readInt();
            if (choice == 0) {
                return;
            }

            Flashcard selected = null;
            for (Flashcard card : allCards) {
                if (card.getCardID() == choice) {
                    selected = card;
                    break;
                }
            }

            if (selected != null) {
                cardDescription(selected);
                return;
            } else {
                System.out.println("Card ID not found. Please try again.");
            }
        }
    }

    void cardDescription(Flashcard card) {
        System.out.println("\n --- Card " + card.getCardID() + " ---\n");
        System.out.println("Question: " + card.getQuestion());
        System.out.println("Answer: " + card.getAnswer());
        System.out.println("Difficulty: " + card.getDifficulty());
        System.out.println("Deck ID: " + (card.getDeck() != null ? card.getDeck().getDeckID() : "N/A"));
        System.out.println("Created at: " + card.getCreatedAt());
    }

    //-----------       HELPER METHODS --------------------
    void printMainMenu() {
        System.out.println(BAR + "\n");
        System.out.println("--- STUDY ASSISTANT APP ---");
        System.out.println("  1. MANAGE decks");
        System.out.println("  2. ALL cards");
        System.out.println("  3. SAVE changes to database");
        System.out.println("  4. EXIT");
        System.out.print("SELECT: ");
    }

    String readLine() { return scanner.nextLine().trim(); }

    int readInt() {
        while (true) {
            try { return Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.print("Enter a valid number: "); }
        }
    }

    void askNextAction() {
        System.out.println(BAR + "\n");
        System.out.println("  [ENTER] Return to menu");
        System.out.println("  [0]     Exit");
        String choice = readLine();
        if ("0".equals(choice)) {
            System.exit(0);
        }
    }
}
