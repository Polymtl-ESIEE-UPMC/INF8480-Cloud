package shared;

import java.io.Serializable;

public class Account implements Serializable {

    public String userName;
    public String password;

    public Account(String userName, String password){
        this.userName = userName;
        this.password = password;
    }
}