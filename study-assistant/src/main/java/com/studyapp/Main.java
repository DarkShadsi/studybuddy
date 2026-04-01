package com.studyapp;

import java.util.Scanner;

import com.studyapp.controller.CustomException;
import com.studyapp.controller.MainController;
import com.studyapp.db.DatabaseConnection;
import com.studyapp.model.Deck;

public class Main {
    public static void main(String[] args) {
        // TODO: Initialize DatabaseConnection
        // TODO: Instantiate DAOs
        // TODO: Instantiate Services
        // TODO: Instantiate Controllers
        // TODO: Create and show MainFrame

        new Main().test();

    }

        //------ TEMPORARY FOR TESTING DAOs ONLY -----------
    void test(){
        Scanner scanner = new Scanner(System.in);
        MainController mc = new MainController();

        System.out.print("Enter db username: ");
        String username = scanner.nextLine();
        System.out.print("Enter db password: ");
        String password = scanner.nextLine();

        try{
            mc.login(username, password);

            while (true) {
                System.out.println("\n1. Add deck\n2. Update deck\n3. Delete deck\n4. Find deck");
                System.out.println("5. Add card\n6. Update card\n7. Delete card\n8. Delete card");
                System.out.print("Enter number: ");
                int choice = scanner.nextInt();
                scanner.nextLine();

                switch (choice) {
                    case 1:
                        System.out.print("Deck name: ");
                        String deckName = scanner.nextLine();
                        System.out.print("Decription: ");
                        String description = scanner.nextLine();
                        mc.createDeck(deckName, description);
                        break;
                    case 2:
                        System.out.print("Deck ID to update: ");
                        int deckID = scanner.nextInt();
                        scanner.nextLine();
                        System.out.print("Deck name: ");
                        String newDeckName = scanner.nextLine();
                        System.out.print("Decription: ");
                        String newDescription = scanner.nextLine();
                        mc.updateDeck(deckID, newDeckName, newDescription);
                        break;
                    case 3:
                        System.out.print("Deck ID to be deleted: ");
                        int deckIDToDelete = scanner.nextInt();
                        scanner.nextLine();
                        mc.deleteDeck(deckIDToDelete);
                        break;
                    case 4:
                        System.out.print("Deck ID to find: ");
                        int deckIDToFind = scanner.nextInt();
                        scanner.nextLine();
                        Deck foundDeck = mc.findDeck(deckIDToFind);
                        if(foundDeck != null){
                            System.out.println("Deck ID: " + foundDeck.getDeckID());
                            System.out.println("Name: " + foundDeck.getName());
                            System.out.println("Descrpition: " + foundDeck.getDescription());
                            System.out.println("Created at: " + foundDeck.getCreatedAt());
                        }else{
                            System.out.println("Deck not found!");
                        }
                        break;


                }
            }
        }catch(CustomException e){
            System.out.println(e.getMessage());
        }
    }
}
