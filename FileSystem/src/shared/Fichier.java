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

    public String name;
    public byte[] content;
    public boolean locked;
    public String lockByUser = "";

    public Fichier(String name, byte[] content) {
        this.name = name;
        this.content = content;
    }

    public Fichier(String name, boolean locked, String lockUser) {
        this.name = name;
        this.locked = locked;
        this.lockByUser = lockUser;
        this.content = new byte[0];
    }

    public Fichier(String name) {
        this.name = name;
        this.content = new byte[0];
    }

    @Override
    public String toString() {
        return String.format("* %-20s%s", name, locked ? "vérouillé par : " + lockByUser : "non vérouillé");
    }
}