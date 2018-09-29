package client;

import shared.Account;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;

import shared.Fichier;
import shared.FileServerInterface;
import shared.MD5CheckSum;

public class GetCommand extends Command {

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException {
        String md5 = MD5CheckSum.generateChecksum(fileName);
        Fichier fichier = fileServer.getFile(account, fileName, md5);
        if (fichier == null) {
            System.out.println("Le fichier local est déjà à jour avec la version du serveur.");
        } else {
            try {
                FileOutputStream stream = new FileOutputStream(fichier.name);
                try {
                    stream.write(fichier.content);
                    System.out.println("Le fichier local a été mis à jour avec la version du serveur.");
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                } finally {
                    try {
                        if (stream != null)
                            stream.close();
                    } catch (IOException e) {
                        System.err.println("Un problème inconnu est survenu : " + e.getMessage());
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
            }
        }
    }
}