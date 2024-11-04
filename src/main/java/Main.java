import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.LinkedHashSet;

import com.google.gson.Gson;

public class Main {
    // TODO: refactor some array conversion of byte[] to Byte[] and viceversia in utils methods
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        String command = args[0];
        switch(command){
            case "decode":{
                String bencodedValue = args[1];
                var index = new Main().new Reference<Integer>(0);
                System.out.println(gson.toJson(formatToString(decodeMessage(bencodedValue.getBytes(), index))));
                System.out.println(new String(encodeMessage(decodeMessage(bencodedValue.getBytes(), new Main().new Reference<Integer>(0)))));
            }
            break;
            case "info":{
                assert args.length > 1 && args[1] != null && !args[1].isEmpty();

                var decodedMessage = decodeMessage(
                    Files.readAllBytes(Paths.get(args[1])),
                    new Main().new Reference<Integer>(0)
                );

                Object formattedFileContent = formatToString(decodedMessage, Set.of("announce", "info", "length"));
                // I am ok with this crashing if the expectations are not met;
                assert formattedFileContent instanceof Map;
                String url = (String)((Map<Object, Object>)formattedFileContent).get("announce");
                Object info = ((Map<String, Object>)formattedFileContent).get("info");
                Long length = ((Map<String, Long>)info).get("length");
                System.out.println("Tracker URL: "+ url);
                System.out.println("Length: "+ length);

                var md = MessageDigest.getInstance("SHA-1");
                md.update(encodeMessage(info));
                System.out.println("Info content hash: " + byteArrayToHexString(md.digest()));
            }
            break;
            case "test": {
                // JUST FOR TESTING
                String fileName = args[1];
                byte[] fileAsByteArr = Files.readAllBytes(Paths.get(fileName));
                var md = MessageDigest.getInstance("SHA-1");
                md.update(fileAsByteArr);
                System.out.println("hash from file is: " + byteArrayToHexString(md.digest()));

                var index = new Main().new Reference<Integer>(0);
                md = MessageDigest.getInstance("SHA-1");
                md.update("d3:foo5:grape5:helloi52ee".getBytes());
                System.out.println("hash from string is: " + byteArrayToHexString(md.digest()));

                var encodedMessage = encodeMessage(decodeMessage(fileAsByteArr, index));
                md = MessageDigest.getInstance("SHA-1");
                md.update(encodedMessage);
                System.out.println("hash from decoded/encoded message: " + byteArrayToHexString(md.digest()));
            }
            break;
            default:
            throw new RuntimeException("unsupported operation");
        }
    }

    public static String byteArrayToHexString(byte[] b) {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }

    public static Object formatToString(Object message){
        return formatToString(message, null);
    }

    public static Object formatToString(Object message, final Set<String> keys){
        assert message != null;

        Object result = new Object();
        switch(message){
            case String s -> result = s;
            case Integer i -> result = i;
            case Long ln -> result = ln;
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

    public static byte[] encodeMessage(Object obj){
        switch(obj){
            case byte[] arr -> {
                return encodeByteArr(arr);
            }
            case String str -> {
                return encodeString(str);
            }
            case Integer num -> {
                return encodeNumber(num);
            }
            case Long num -> {
                return encodeNumber(num);
            }
            case List lst -> {
                return encodeList(lst);
            }
            case Map map -> {
                return encodeMap(map);
            }
            default -> {
                throw new RuntimeException("Unsupported format");
            }
        }
    }

    public static byte[] encodeByteArr(byte[] arr){
        assert arr != null;
        if(arr.length == 0) return "0:".getBytes();

        byte[] lng = String.valueOf(arr.length).getBytes();
                        // length n  :   word
        var resp = new byte[lng.length + 1 + arr.length];
        for(int i = 0; i < resp.length; i++){
            if(i < lng.length)
                resp[i] = lng[i];
            else if(i == lng.length)
                resp[i] = ':';
            else
                resp[i] = arr[i - 1 - lng.length];
        }

        return resp;
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

    public static byte[] encodeString(String str){
        assert str != null;
        return encodeByteArr(str.getBytes());
    }

    public static Object decodeBencodeNumber(byte[] bencodedString, Reference<Integer> index) {
        if(bencodedString == null) return null;
        assert index.getValue() < bencodedString.length - 1;

        int begin = index.getValue() + 1; //skip the i
        int end = indexOfInByteArr(bencodedString, 'e', begin);
        index.setValue(end);

        assert begin <= end;

        return Long.valueOf(new String(Arrays.copyOfRange(bencodedString, begin, end)));
    }

    public static byte[] encodeNumber(Integer num){
        assert num != null;
        return encodeNumber(Long.valueOf(num));
    }

    public static byte[] encodeNumber(Long num){
        assert num != null;
        return ('i' + String.valueOf(num) + 'e').getBytes();
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

    public static byte[] encodeList(List lst){
        assert lst != null;
        if(lst.isEmpty()) return ('l' + "" + 'e').getBytes();
        var respByte = new ArrayList<Byte>();
        for(Object elem : lst){
            for(byte tmp : encodeMessage(elem)) respByte.add(Byte.valueOf(tmp));
        }
        var resp = new byte[respByte.size() + 2];
        resp[0] = (byte)'l';
        resp[resp.length - 1] = (byte)'e';
        for(int i = 1; i < resp.length - 1; i++){
            resp[i] = respByte.get(i-1).byteValue();
        }
        return resp;
    }

    public static Map<byte[], Object> decodeBencodeDictionary(byte[] bencodedString, Reference<Integer> index) {
        assert index.getValue() < bencodedString.length - 1;

        if(bencodedString == null) return null;
        if(bencodedString.length == 0) return new HashMap<byte[], Object>();

        var decodedDic = new LinkedHashMap<byte[], Object>();
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

    public static byte[] encodeMap(Map dic){
        assert dic != null;
        if(dic.size() == 0) return ('d' + "" + 'e').getBytes();

        var respByte = new ArrayList<Byte>();
        Set<Map.Entry> set = dic.entrySet();
        for(Entry entry : set){
            for(byte tmp : encodeMessage(entry.getKey()))
                respByte.add(Byte.valueOf(tmp));
            for(byte tmp : encodeMessage(entry.getValue()))
                respByte.add(Byte.valueOf(tmp));
        }

        var resp = new byte[respByte.size() + 2];
        resp[0] = (byte)'d';
        resp[resp.length - 1] = (byte)'e';
        for(int i = 1; i < resp.length - 1; i++){
            resp[i] = respByte.get(i-1).byteValue();
        }
        return resp;
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
