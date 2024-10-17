import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.lang.Character;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Main {
  private static final Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    String command = args[0];
    if("decode".equals(command)) {
      String bencodedValue = args[1];
      var index = new Main().new Reference<Integer>(0);
      System.out.println(gson.toJson(decodeMessage(bencodedValue, index)));
    } else {
      System.out.println("Unknown command: " + command);
    }

  }

  public static Object decodeMessage(String bencodedString, Reference<Integer> index){
      switch(bencodedString){
        case String message when Character.isDigit(message.charAt(index.getValue())) -> {
          return decodeBencodeString(message, index);
        }
        case String message when message.charAt(index.getValue()) == 'i' -> {
          return decodeBencodeNumber(bencodedString, index);
        }
        case String message when message.charAt(index.getValue()) == 'l' -> {
          return decodeBencodeList(bencodedString, index);
        }
        case String message when message.charAt(index.getValue()) == 'd' -> {
          return decodeBencodeDictionary(bencodedString, index);
        }
        default -> {
          throw new RuntimeException("Unsupported format");
        }
      }
  }

  public static String decodeBencodeString(String bencodedString, Reference<Integer> index) {
    assert index.getValue() < bencodedString.length() - 1;
    if(bencodedString == null || bencodedString.isEmpty()) return bencodedString;

    int firstColonIndex = bencodedString.indexOf(':', index.getValue());
    int end = firstColonIndex + Integer.parseInt(bencodedString.substring(index.getValue(), firstColonIndex));
    index.setValue(end);
    return bencodedString.substring(
      firstColonIndex + 1,
      end + 1 //exclusive, strings have no end character
    );
  }

  public static Long decodeBencodeNumber(String bencodedString, Reference<Integer> index) {
    assert index.getValue() < bencodedString.length() - 1;
    if(bencodedString == null || bencodedString.isEmpty()) return null;

    int end = bencodedString.indexOf('e', index.getValue());
    int begin = index.getValue() + 1; //skip the i
    index.setValue(end);

    return Long.valueOf(bencodedString.substring(begin, end));
  }

  public static List<Object> decodeBencodeList(String bencodedString, Reference<Integer> index){
    assert index.getValue() < bencodedString.length() - 1;
    if(bencodedString == null) return null;
    if(bencodedString.length() < 3) return new ArrayList<Object>();

    var decodedList = new ArrayList<Object>();

    while(index.getValue() + 2 < bencodedString.length() && bencodedString.charAt(index.getValue() + 1) != 'e'){
      index.setValue(index.getValue()+1); // move to the begin of the next element, assuming we left off at the last
      decodedList.add(decodeMessage(bencodedString, index));
    }
    index.setValue(index.getValue() + 1); // move to the next, for recursive calls and such, basically on the ending e
    return decodedList;
  }

  public static Map<String, Object> decodeBencodeDictionary(String bencodedString, Reference<Integer> index) {
    assert index.getValue() < bencodedString.length() - 1;

    if(bencodedString == null) return null;
    if(bencodedString.isEmpty()) return new HashMap<String, Object>();

    var decodedDic = new HashMap<String, Object>();
    while(index.getValue() + 2 < bencodedString.length() && bencodedString.charAt(index.getValue() + 1) != 'e'){
      index.setValue(index.getValue()+1); // move to the begin of the next element, assuming we left off at the last
      // TODO: check the next is a string?
      // TODO: think if there is some optimization that can be done here since we knwo the next is a string;
      var k = (String)decodeMessage(bencodedString, index);
      index.setValue(index.getValue() + 1); // moving to the next element
      var v = decodeMessage(bencodedString, index);
      decodedDic.put(k, v);
    }

    return decodedDic;
  }

  /**
   * Used to keep track of the start/end index of the element we are decoding.
   * The goal is to move the index but never actually modify the original string.
   * At the beginning of a decoding method the value should be at the first index of the element we are decoding.
   * At the end of the decoding method the value should be at the last index of the element we are decoding.
   *
   * In general:
   *  this can also be used to pass a reference to anything when the pass by value policy of java is not what we need
   */
  private class Reference<T>{
    private T value;

    Reference(T value){
      this.value = value;
    }

    public void setValue(T value){
      this.value = value;
    }

    public T getValue(){
      return this.value;
    }
  }
}
