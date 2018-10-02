package shared;

import java.io.Serializable;

//Classe contenant un nom d'utilisateur et un mot de passe. Utilisée pour moins encombrer les appels RMI
public class Account implements Serializable {

    public String userName;
    public String password;

    public Account(String userName, String password){
        this.userName = userName;
        this.password = password;
    }
}