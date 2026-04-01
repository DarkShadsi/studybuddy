package com.studyapp.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import com.studyapp.dao.impl.DeckDAOImpl;

//A HELPER CLASS FOR CREATING NEW OBJECT INSTANCES
public class ObjectFactory {

    //HELPER METHOD FOR CREATING A NEW DECK OBJECT
    public Deck createNewDeck(ResultSet rs){
    try{
        Deck deck = new Deck();
        deck.setDeckID(rs.getInt("deck_id"));
        deck.setName(rs.getString("name"));
        deck.setDescription(rs.getString("description"));
        
        Object createdAtObj = rs.getObject("created_at");
        if (createdAtObj instanceof LocalDateTime) {
            deck.setCreatedAt((LocalDateTime) createdAtObj);
        } else if (createdAtObj instanceof java.sql.Timestamp) {
            deck.setCreatedAt(((java.sql.Timestamp) createdAtObj).toLocalDateTime());
        }
        
        return deck;
    }catch(SQLException e) {
        e.printStackTrace();
    }
        return null;
    }

    //HELPER METHOD FOR CREATING A NEW FLASHCARD OBJECT
    public Flashcard createNewCard(ResultSet rs){
    try{
        Flashcard card = new Flashcard();
        card.setCardID(rs.getInt("card_id"));
        card.setQuestion(rs.getString("question"));
        card.setAnswer(rs.getString("answer"));
        card.setDifficulty(rs.getString("difficulty"));

        //CREATE NEW DECk OBJECT
        Deck deck = new DeckDAOImpl().findByID(rs.getInt("deck_id"));
        card.setDeck(deck);
        
        Object createdAtObj = rs.getObject("created_at");
        if (createdAtObj instanceof LocalDateTime) {
            card.setCreatedAt((LocalDateTime) createdAtObj);
        } else if (createdAtObj instanceof java.sql.Timestamp) {
            card.setCreatedAt(((java.sql.Timestamp) createdAtObj).toLocalDateTime());
        }
        
        return card;
    }catch(SQLException e) {
        e.printStackTrace();
    }
        return null;
    }
}
