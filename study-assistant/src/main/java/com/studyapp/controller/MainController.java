package com.studyapp.controller;

import java.sql.SQLException;
import java.time.LocalDateTime;

import com.studyapp.dao.impl.DeckDAOImpl;
import com.studyapp.db.DatabaseConnection;
import com.studyapp.model.Deck;

//HANDLES ALL OPERATIONS THAT CONNECTS BACKEND WITH FRONTEND
//INCLUDES:
//CRUD OPEARTIONS
//DATA/FILE HANDLING
//AUTHENTICATION
//DATA VALIDATION

public class MainController {
    DeckDAOImpl deckDaoImpl = new DeckDAOImpl();

    // AUTHENTICATION
    public void login(String username, String password) throws CustomException{
        if (!DatabaseConnection.authenticate(username, password)) {
            throw new CustomException("Invalid credentials.");
        }
        DatabaseConnection.setCredentials(username, password);
    }

    //DMLs
    public void createDeck(String deckName, String description) throws CustomException{
        try{
            Deck deck = new Deck(999, deckName, description, LocalDateTime.now());
            deckDaoImpl.insert(deck);
        }catch(SQLException e){
            throw new CustomException("Error adding deck.");
        }
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
        try{
            deckDaoImpl.delete(deckID);
        }catch(SQLException e){
            throw new CustomException("Error deleting deck.");
        }
    }

    public Deck findDeck(int deckID){
        return deckDaoImpl.findByID(deckID);
    }
}
