import com.google.gson.Gson;
import java.lang.Character;
import java.util.ArrayList;
import java.util.List;
// import com.dampcake.bencode.Bencode; - available if you need it!

public class Main {
  private static final Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    String command = args[0];
    if("decode".equals(command)) {
      String bencodedValue = args[1];
      System.out.println(gson.toJson(new Main().new DecodedMessage(bencodedValue).decodeMessage()));
    } else {
      System.out.println("Unknown command: " + command);
    }

  }

  public class DecodedMessage {
    private String originalMessage;
    private Integer end;

    public DecodedMessage(String message){
      this.originalMessage = message;
    }

    Object decodeMessage(){
      switch(this.originalMessage){
        case String message when Character.isDigit(message.charAt(0)) ->{
          return decodeBencodeString(message);
        }
        case String message when message.charAt(0) == 'i' ->{
          return decodeBencodeNumber(message);
        }
        case String message when message.charAt(0) == 'l' ->{
          return decodeBencodeList(message);
        }
        default ->{
          throw new RuntimeException("Unsupported format");
        }
      }
    }

    String decodeBencodeString(String bencodedString) {
      int firstColonIndex = 0;
      for(int i = 0; i < bencodedString.length(); i++) {
        if(bencodedString.charAt(i) == ':') {
          firstColonIndex = i;
          break;
        }
      }
      int length = Integer.parseInt(bencodedString.substring(0, firstColonIndex));
      this.end = firstColonIndex+length;
      return bencodedString.substring(firstColonIndex+1, firstColonIndex+1+length);
    }

    Long decodeBencodeNumber(String bencodedString) {
      this.end = bencodedString.indexOf('e');
      return Long.valueOf(bencodedString.substring(1, this.end));
    }

    List<Object> decodeBencodeList(String bencodeString){
      if(bencodeString == null || bencodeString.length() < 3) return new ArrayList<Object>();

      var messageCopy = bencodeString.substring(1);
      var resp = new ArrayList<Object>();
      while(messageCopy.length() > 1 && messageCopy.charAt(0) != 'e'){
        switch(messageCopy){
          case String messageTemp when Character.isDigit(messageTemp.charAt(0)) ->{
            resp.add(decodeBencodeString(messageTemp));
            messageCopy = messageTemp.substring(this.end+1);
          }
          case String messageTemp when messageTemp.charAt(0) == 'i' ->{
            resp.add(decodeBencodeNumber(messageTemp));
            messageCopy = messageTemp.substring(this.end+1);
          }
          case String messageTemp when messageTemp.charAt(0) == 'l' ->{
            resp.add(decodeBencodeList(messageTemp));
            messageCopy = messageTemp.substring(this.end + 1);
          }
          default ->{
            throw new RuntimeException("Unsupported format");
          }
        }
      }
      this.end = bencodeString.length() - messageCopy.length();
      return resp;
    }

  }

}
