package eu.postgresql.android.conferencescanner;

public class TokenType {
    public static String tokenIsFrom(String tokentype) {
        if (tokentype.equals("id")) {
            return "ticket";
        }
        else if (tokentype.equals("at")) {
            return "badge";
        }
        else {
            return "unknown";
        }
    }
}
