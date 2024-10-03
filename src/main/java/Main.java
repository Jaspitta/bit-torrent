import com.google.gson.Gson;
import java.lang.Character;
// import com.dampcake.bencode.Bencode; - available if you need it!

public class Main {
  private static final Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    // System.out.println("Logs from your program will appear here!");
    String command = args[0];
    if("decode".equals(command)) {
      //  Uncomment this block to pass the first stage
      String bencodedValue = args[1];
      switch(bencodedValue){
        case String message when Character.isDigit(message.charAt(0)) ->{
          System.out.println(gson.toJson(decodeBencodeString(message)));
        }
        case String message when message.charAt(0) == 'i' ->{
          System.out.println(decodeBencodeNumber(message));
        }
        default ->{
          throw new RuntimeException("Unsupported format");
        }
      }
    } else {
      System.out.println("Unknown command: " + command);
    }

  }

  static String decodeBencodeString(String bencodedString) {
      int firstColonIndex = 0;
      for(int i = 0; i < bencodedString.length(); i++) {
        if(bencodedString.charAt(i) == ':') {
          firstColonIndex = i;
          break;
        }
      }
      int length = Integer.parseInt(bencodedString.substring(0, firstColonIndex));
      return bencodedString.substring(firstColonIndex+1, firstColonIndex+1+length);
  }

  static String decodeBencodeNumber(String bencodedString) {
      return bencodedString.substring(1, bencodedString.length() - 1);
  }

}
