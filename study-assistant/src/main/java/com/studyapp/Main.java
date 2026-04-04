package com.studyapp;

import com.studyapp.controller.MainController;
import com.studyapp.view.CLIView;


public class Main {
    public static void main(String[] args) {
        // TODO: Initialize DatabaseConnection
        // TODO: Instantiate DAOs
        // TODO: Instantiate Services
        // TODO: Instantiate Controllers
        // TODO: Create and show MainFrame

        new CLIView(new MainController()).start();

    }

}
