package eu.postgresql.android.conferencescanner;

public class TokenType {
    public static String tokenIsFrom(String tokentype) {
        if (tokentype.equals("id")) {
            return "Ticket";
        }
        else if (tokentype.equals("at")) {
            return "Badge";
        }
        else {
            return "Unknown";
        }
    }
}
