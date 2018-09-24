package client;

import shared.Account;
import java.rmi.RemoteException;
import shared.AuthServerInterface;
import shared.FileServerInterface;

public class CreateCommand extends Command{

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException{
        boolean success = fileServer.createFile(account, fileName);
        System.out.println("L'opération a " + ((success) ? "réussie." : "échouée."));
    }
}