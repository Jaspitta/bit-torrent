import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

public class Main {
  private static final Gson gson = new Gson();

  public static void main(String[] args) throws Exception {
    String command = args[0];
    switch(command){
      case "decode":{
        String bencodedValue = args[1];
        var index = new Main().new Reference<Integer>(0);
        System.out.println(formatToString(decodeMessage(bencodedValue.getBytes(), index)));
      }
      break;
      case "info":{
        assert args.length > 1 && args[1] != null && !args[1].isEmpty();
        var index = new Main().new Reference<Integer>(0);
        String fileName = args[1];
        byte[] fileAsByteArr = Files.readAllBytes(Paths.get(fileName));
        Object formattedFileContent = formatToString(decodeMessage(fileAsByteArr, index), Set.of("announce", "info", "length"));
        // I am ok with this crashing if the expectations are not met;
        assert formattedFileContent instanceof Map;
        String url = ((Map<Object, String>)formattedFileContent).get("announce");
        String length = ((Map<Object, Map<String, String>>)formattedFileContent).get("info").get("length");
        System.out.println("Tracker URL: "+ url);
        System.out.println("Length: "+ length);
      }
      break;
      default:
      throw new RuntimeException("unsupported operation");
    }
  }

    public static Object formatToString(Object message){
        return formatToString(message, null);
    }

    public static Object formatToString(Object message, final Set<String> keys){
        assert message != null;

        Object result = new Object();
        switch(message){
            case String s -> result = s;
            case Integer i -> result = i.toString();
            case Long ln -> result = ln.toString();
            case List ls -> {
                assert ls != null;
                var tmp = new ArrayList<Object>();
                ls.forEach((element) -> tmp.add(formatToString(element, keys)));
                result = tmp;
            }
            case Map m -> {
                assert m != null;
                var map = new HashMap<Object, Object>();
                m.forEach( (k, v) -> {
                    var formattedKey = formatToString(k);
                    var formattedValue = keys == null || keys.isEmpty() || keys.contains(formattedKey) || keys.contains(".") ? formatToString(v, keys) : v;
                    map.put(formattedKey, formattedValue);

                });
                result = map;
            }
            case byte[] s -> result = new String(s);
            default -> throw new RuntimeException("unparsable object");
        }

        return result;
    }

  public static Object decodeMessage(byte[] bencodedString, Reference<Integer> index){
      switch(bencodedString){
        case byte[] arr when Character.isDigit(arr[index.getValue()]) -> {
          return decodeBencodeString(arr, index);
        }
        case byte[] arr when arr[index.getValue()] == 'i' -> {
          return decodeBencodeNumber(arr, index);
        }
        case byte[] arr when arr[index.getValue()] == 'l' -> {
          return decodeBencodeList(arr, index);
        }
        case byte[] arr when arr[index.getValue()] == 'd' -> {
          return decodeBencodeDictionary(arr, index);
        }
        default -> {
          throw new RuntimeException("Unsupported format");
        }
      }
  }

  public static byte[] decodeBencodeString(byte[] bencodedString, Reference<Integer> index) {
    if(bencodedString == null) return bencodedString;
    assert index.getValue() < bencodedString.length - 1;

    int firstColonIndex = indexOfInByteArr(bencodedString, ':', index.getValue());
    int end = firstColonIndex + Integer.parseInt(new String(Arrays.copyOfRange(bencodedString, index.getValue(), firstColonIndex)));
    index.setValue(end);
    return Arrays.copyOfRange(
      bencodedString,
      firstColonIndex + 1,
      end + 1 //exclusive, strings have no end character
    );
  }

  public static byte[] decodeBencodeNumber(byte[] bencodedString, Reference<Integer> index) {
    if(bencodedString == null) return null;
    assert index.getValue() < bencodedString.length - 1;

    int begin = index.getValue() + 1; //skip the i
    int end = indexOfInByteArr(bencodedString, 'e', begin);
    index.setValue(end);

    assert begin <= end;
    return Arrays.copyOfRange(bencodedString, begin, end);
  }

  public static List<Object> decodeBencodeList(byte[] bencodedString, Reference<Integer> index){
    assert index.getValue() < bencodedString.length - 1;
    if(bencodedString == null) return null;
    if(bencodedString.length < 3) return new ArrayList<Object>();

    var decodedList = new ArrayList<Object>();

    while(index.getValue() + 2 < bencodedString.length && bencodedString[index.getValue()+1] != 'e'){
      index.setValue(index.getValue()+1); // move to the begin of the next element, assuming we left off at the last
      decodedList.add(decodeMessage(bencodedString, index));
    }
    index.setValue(index.getValue() + 1); // move to the next, for recursive calls and such, basically on the ending e
    return decodedList;
  }

  public static Map<byte[], Object> decodeBencodeDictionary(byte[] bencodedString, Reference<Integer> index) {
    assert index.getValue() < bencodedString.length - 1;

    if(bencodedString == null) return null;
    if(bencodedString.length == 0) return new HashMap<byte[], Object>();

    var decodedDic = new HashMap<byte[], Object>();
    while(index.getValue() + 2 < bencodedString.length && bencodedString[index.getValue() + 1] != 'e'){
      index.setValue(index.getValue()+1); // move to the begin of the next element, assuming we left off at the last
      // TODO: check the next is a string?
      // TODO: think if there is some optimization that can be done here since we knwo the next is a string;
      var k = (byte[])decodeMessage(bencodedString, index);
      index.setValue(index.getValue() + 1); // moving to the next element
      var v = decodeMessage(bencodedString, index);
      decodedDic.put(k, v);
    }

    return decodedDic;
  }

  public static int indexOfInByteArr(byte[] message, Character element, int from){
    assert message != null && message.length != 0;
    assert from > 0 && from < message.length;
    for(int i = from; i < message.length ;i++){
      if(message[i] == element) return i;
    }

    return -1;
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
