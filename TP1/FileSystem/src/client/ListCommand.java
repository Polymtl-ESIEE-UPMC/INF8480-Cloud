package client;

import shared.Account;
import java.rmi.RemoteException;
import java.util.List;

import shared.AuthServerInterface;
import shared.Fichier;
import shared.FileServerInterface;

public class ListCommand extends Command{

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException{
        //récupère la liste des fichiers sur le serveur de fichiers
        List<Fichier> files = fileServer.listFiles(account);
        for(Fichier file : files){
            System.out.println(file);
        }
        System.out.println(files.size() + " fichier(s)");
    }
}