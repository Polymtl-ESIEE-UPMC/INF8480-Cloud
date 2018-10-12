package shared;

public class OperationTodo {
    public String name;
    public int parameter;

    public OperationTodo(String name, int parameter) {
        this.name = name;
        this.parameter = parameter;
    }

    public int execute() {
        if (name.equals("pell")) {
            return Operations.pell(parameter);
        } else {
            return Operations.prime(parameter);
        }
    }
}