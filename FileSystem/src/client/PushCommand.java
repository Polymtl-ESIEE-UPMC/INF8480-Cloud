package client;

import shared.Account;
import java.rmi.RemoteException;
import shared.AuthServerInterface;
import shared.FileServerInterface;
import shared.Fichier;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;

public class PushCommand extends Command {

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException {
        String filePath = fileName;
        File file = new File(filePath);
        if (file.exists()) {
            // envoyer le fichier
            byte[] fileContent = null;
            try {
                fileContent = Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                System.err.println("Could not read " + file.getName() + " contents.");
                return;
            }
            if (fileServer.pushFile(account, fileName, fileContent)) {
                System.out.println(fileName + " a correctement été envoyé au serveur.");
            } else {
                System.out.println("Une erreur s'est produite sur le serveur.");
            }
        } else {
            System.err.println("Ce fichier n'existe pas en local.");
        }
    }
}