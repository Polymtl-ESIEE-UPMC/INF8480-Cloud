package client;

import shared.Account;
import java.rmi.RemoteException;
import shared.AuthServerInterface;
import shared.FileServerInterface;

public class CreateCommand extends Command {

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException {
        //demande au serveur de fichiers de créé le fichier désiré
        boolean success = fileServer.createFile(account, fileName);
        if (success) {
            System.out.println(fileName + " a bien été créé.");
        } else {
            System.out.println("L'opération a échouée.");
        }
    }
}