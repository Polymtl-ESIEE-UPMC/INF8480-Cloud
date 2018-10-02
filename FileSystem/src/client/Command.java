package client;

import java.rmi.RemoteException;

import shared.Account;
import shared.AuthServerInterface;
import shared.FileServerInterface;

//Classe abstraite utilisée pour séparer plus facilement le comportement de chaque commande
public abstract class Command{

    /**
     * Exécute la commande
     * account : le compte contenant le nom d'utilisateur et le mos de passe
     * fileServer : le stub RMI
     * fileName : le nom de fichier, si applicable
     */
    public abstract void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException;

}