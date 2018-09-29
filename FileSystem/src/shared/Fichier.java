package shared;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

public class Fichier implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String FILES_DIR_NAME = "files";

    public String name;
    public byte[] content;
    public String lock;
    public String lockByUser = "";

    public Fichier(String name, byte[] content) {
        this.name = name;
        this.content = content;
        this.lock = FILES_DIR_NAME + "/." + name + "_lockstate";
    }

    public Fichier(String name, boolean legacy, String legacy1) {
        this.name = name;
        this.content = new byte[0];
        this.lock = FILES_DIR_NAME + "/." + name + "_lockstate";
    }

    public Fichier(String name) {
        this.name = name;
        this.content = new byte[0];
        this.lock = FILES_DIR_NAME + "/." + name + "_lockstate";
    }

    @Override
    public String toString() {
        return String.format("* %-20s%s", name, lockState() ? "vérouillé par : " + lockByUser : "non vérouillé");
    }

    public boolean lockState() {
        try {
            FileReader fileReader = new FileReader(lock);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String[] state = new String[2];
            for (int i = 0; i < 2; i++) {
                state[i] = bufferedReader.readLine();
            }
            bufferedReader.close();
            lockByUser = state[1];
            return Boolean.parseBoolean(state[0]);
        } catch (FileNotFoundException e) {
            System.out.println("Unable to open file '" + lock + "'");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void createLock() throws IOException {
        File file = new File(lock);
        if (file.exists()) {
            throw new IOException("Lock existe deja sur le serveur!");
        } else {
            changeLockState(String.valueOf(false), "");
        }
    }

    private Response changeLockState(String state, String username) {
        try {
            FileWriter fileWriter = new FileWriter(lock);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(state);
            bufferedWriter.newLine();
            bufferedWriter.write(username);
            bufferedWriter.close();
            return new Response(1, "SUCCES: Changer l'etat de lock en " + state + " avec succes");
        } catch (IOException e) {
            e.printStackTrace();
        }
        int code = 101;
        return new Response(code, "ERROR " + code + ": Echouer de changer l'etat de lock en " + state);
    }

    public Response lock_fichier(Account account) {
        if (!lockState()) {
            lockByUser = account.userName;
            return changeLockState(String.valueOf(true), lockByUser);
        }
        int code = 102;
        return new Response(code, "ERROR " + code + ": Le fichier est deja verouille");
    }

    public Response unlock_fichier(Account account) {
        if (lockState()) {
            if (lockByUser.equals(account.userName)) {
                lockByUser = "";
                return changeLockState(String.valueOf(false), lockByUser);
            } else {
                int code = 104;
                return new Response(code, "PERMISSION DENIED " + code + ": Le fichier est verouille par " + lockByUser);
            }
        } else {
            int code = 103;
            return new Response(code, "ERROR " + code + ": Le fichier est non verouille");
        }
    }
}