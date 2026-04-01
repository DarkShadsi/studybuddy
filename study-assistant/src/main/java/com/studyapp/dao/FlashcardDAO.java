package com.studyapp.dao;

import java.sql.SQLException;

import com.studyapp.model.Flashcard;

public interface FlashcardDAO {
    public void insert(Flashcard flashcard) throws SQLException;
    public void update(Flashcard flashcard) throws SQLException;
    public void delete(int cardID) throws SQLException;
    public Flashcard findByID(int cardID);
}
