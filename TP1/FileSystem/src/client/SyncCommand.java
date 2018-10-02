package client;

import shared.Account;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;

import shared.AuthServerInterface;
import shared.Fichier;
import shared.FileServerInterface;

public class SyncCommand extends Command {

    public void run(Account account, FileServerInterface fileServer, String fileName) throws RemoteException {
        //récupère les fichiers sur le serveur de fichiers
        List<Fichier> files = fileServer.syncLocalDirectory(account);
        
        try {
            //pour tous les fichiers retournés, les sauvegarder en local
			for (final Fichier file : files) {
                FileOutputStream stream = new FileOutputStream(file.name);
                try {
                    stream.write(file.content);
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
            }
			
		} catch (FileNotFoundException e) {
			System.err.println(e.getMessage());
		}
    }
}