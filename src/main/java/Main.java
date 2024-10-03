import com.google.gson.Gson;
import java.lang.Character;
import java.util.ArrayList;
import java.util.List;
// import com.dampcake.bencode.Bencode; - available if you need it!

public class Main {
  private static final Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    // System.out.println("Logs from your program will appear here!");
    String command = args[0];
    if("decode".equals(command)) {
      String bencodedValue = args[1];
      System.out.println(decodeBencodeMessage(bencodedValue));
    } else {
      System.out.println("Unknown command: " + command);
    }

  }

  static Object decodeBencodeMessage(String bencodedMessage){
    switch(bencodedMessage){
      case String message when Character.isDigit(message.charAt(0)) ->{
        return gson.toJson(decodeBencodeString(message));
      }
      case String message when message.charAt(0) == 'i' ->{
        return decodeBencodeNumber(message);
      }
      case String message when message.charAt(0) == 'l' ->{
        return gson.toJson(decodeBencodeList(message));
      }
      default ->{
        throw new RuntimeException("Unsupported format");
      }
    }
  }

  static List<Object> decodeBencodeList(String bencodeString){
        var messageCopy = bencodeString.substring(1, bencodeString.length() - 2);
        var resp = new ArrayList<Object>();
        while(messageCopy.length() > 1){
          switch(messageCopy){
            case String messageTemp when Character.isDigit(messageTemp.charAt(0)) ->{
              resp.add(decodeBencodeString(messageCopy));
              messageCopy = messageCopy.substring(Integer.valueOf(messageCopy.substring(0, messageCopy.indexOf(':'))) + 2);
            }
            case String messageTemp when messageTemp.charAt(0) == 'i' ->{
              resp.add(decodeBencodeNumber(messageCopy));
              messageCopy = messageCopy.substring(messageCopy.indexOf('e') + 1);
            }
            default ->{
              throw new RuntimeException("Unsupported format");
            }
          }
        }
        return resp;
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
    return bencodedString.substring(1, bencodedString.indexOf('e'));
  }

}
