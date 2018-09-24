package client;

import shared.Account;
import java.rmi.RemoteException;
import java.util.List;

import shared.AuthServerInterface;
import shared.Fichier;
import shared.FileServerInterface;

public class ListCommand extends Command{

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException{
        List<Fichier> files = fileServer.listFiles(account);
        for(Fichier file : files){
            System.out.println(file);
        }
        System.out.println(files.size() + " fichier(s)");
    }
}