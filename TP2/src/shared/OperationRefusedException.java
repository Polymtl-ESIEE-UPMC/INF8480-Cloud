package shared;

public class OperationRefusedException extends Exception{

    public OperationRefusedException(String message){
        super(message);
    }
}