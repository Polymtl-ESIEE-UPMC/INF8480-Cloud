package client;

import shared.Account;
import shared.Response;
import java.rmi.RemoteException;
import shared.AuthServerInterface;
import shared.FileServerInterface;
import shared.Fichier;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;

public class PushCommand extends Command {

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException, IOException {
        Fichier fichier = null;
        String filePath = fileName;
        File file = new File(filePath);
        if (file.exists()) {
            // envoyer le fichier
            try {
                byte[] fileContent = Files.readAllBytes(file.toPath());
                fichier = new Fichier(file.getName(), fileContent);
                Response res = fileServer.pushFile(account, fileName, fileContent);
                System.out.println(res.msg);
            } catch (RemoteException e) {
                System.err.println("Could not send " + file.getName() + " contents.");
            } catch (IOException e) {
                System.err.println("Could not read " + file.getName() + " contents.");
            }
        } else {
            throw new RemoteException("Ce fichier n'existe pas en local");
        }
    }
}