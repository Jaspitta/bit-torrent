import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

import com.google.gson.Gson;

public class Main {
    // TODO: decide on the aproach we want to take, should I mutate incoming objects in my functions
    // or creating a new one
    //
    // TODO: refactor some array conversion of byte[] to Byte[] and viceversia in utils methods
    private static final Gson gson = new Gson();
    private static final Main main = new Main();
    private static final String HASH_TYPE = "SHA-1";

    public static void main(String[] args) throws Exception {
        switch(args[0]){
            case "decode": {
                assert args.length > 1 && args[1] != null && !args[1].isEmpty();
                System.out.println(
                    gson.toJson(
                        formatToString(
                            decodeMessage(
                                args[1].getBytes(),
                                main.getNewReference(0)
                            )
                        )
                    )
                );
            }
            break;
            case "info": {
                assert args.length > 1 && args[1] != null && !args[1].isEmpty();

                var decodedMessage = getDecodedMessageFromFile(args[1]);

                Object formattedFileContent = formatToString(decodedMessage, Set.of("announce", "info", "length"));

                assert formattedFileContent instanceof Map;
                String url = extractElement((Map<String, String>)formattedFileContent, "announce");
                Object info = extractElement((Map<String, String>)formattedFileContent, "info");
                Long length = extractElement((Map<String, Long>)info, "length");

                System.out.println("Info content hash: " +
                    byteArrayToHexString(
                        calculateHash(
                            encodeMessage(info)
                        )
                    )
                );

                Long pieceLength = extractElement((Map<String, Long>)info, "piece length");
                byte[][] pieceHashesFormatted = arrToMatrix(extractElement((Map<String, byte[]>)info, "pieces"), 20);

                System.out.println("Tracker URL: "+ url);
                System.out.println("Length: "+ length);
                System.out.println("Piece Length: "+ pieceLength);
                System.out.println("Piece Hashes: ");

                for(byte[] arr : pieceHashesFormatted) System.out.println(byteArrayToHexString(arr));
            }
            break;
            case "peers": {
                assert args.length > 1 && args[1] != null && !args[1].isEmpty();

                var decodedMessage = getDecodedMessageFromFile(args[1]);

                Object formattedFileContent = formatToString(decodedMessage, Set.of("announce", "info", "length"));
                // I am ok with this crashing if the expectations are not met;
                assert formattedFileContent instanceof Map;
                Object info = extractElement((Map<String, Object>)formattedFileContent, "info");

                var peerReq = createPeerRequest(
                    extractElement((Map<String, String>)formattedFileContent, "announce"),
                    null,
                    6881,
                    0,
                    0,
                    extractElement((Map<String, Long>)info, "length"),
                    calculateHash(encodeMessage(info))
                );

                var resp = formatPeersResp(
                    HttpClient.newBuilder().build().send(
                        peerReq,
                        BodyHandlers.ofByteArray()
                    )
                );

                for(String peer : resp){
                    System.out.println(peer);
                }

            }
            break;
            case "handshake": {
                assert args.length >= 3 && args[1] != null && !args[1].isEmpty() && args[2] != null && !args[2].isEmpty();

                var decodedMessage = getDecodedMessageFromFile(args[1]);

                Object formattedFileContent = formatToString(decodedMessage, Set.of("announce", "info", "length"));
                assert formattedFileContent instanceof Map;
                Object info = extractElement((Map<String, Object>)formattedFileContent, "info");
                var hash = calculateHash(encodeMessage(info));

                int ipPortSeparator = args[2].indexOf(':');

                // TODO: using passed values as inputs withouth sanythization is never a good idea for security reasons
                try(var clientSocket = new Socket(args[2].substring(0, ipPortSeparator), Integer.valueOf(args[2].substring(ipPortSeparator+1)))){

                    var outStream = clientSocket.getOutputStream();

                    // length of protocol: 19
                    // BitTorrentProtocol
                    // 8 bytes to 0
                    // sha1 as bytes (20)
                    // peerId (20 random bytes)
                    var message = new byte[68];
                    int i = 0;
                    message[i] = 19;
                    i++;
                    for(byte b : "BitTorrent protocol".getBytes()){
                        message[i] = b;
                        i++;
                    }
                    while(i < 28){
                        message[i] = 0;
                        i++;
                    }
                    for(byte b : hash){
                        message[i] = b;
                        i++;
                    }
                    for(byte b : "00112233445566778899".getBytes()){
                        message[i] = b;
                        i++;
                    }
                    outStream.write(message);
                    var inStream = clientSocket.getInputStream();

                    // should I check the hash back is the same?
                    var handShakeResp = inStream.readNBytes(68);

                    for(int j = 0; j < hash.length; j ++)
                        assert hash[j] == handShakeResp[j+28];

                    System.out.println(
                        "Peer ID: " +
                        byteArrayToHexString(
                            Arrays.copyOfRange(handShakeResp, 48, 68)
                        )
                    );
                }
            }
            break;
            case "download_piece": {

                // download_piece -o output_file torrent_file piece_index

                // get tracker url
                // get peers
                // hadshake with one

                // peer messages
                    // length (4)
                    // id (1)
                    // payload (variable)


                // wait for bitfield message
                    // id = 5
                    // payload = pieces available, ignore for now
                // send interested message
                    // id = 2
                    // payload empty
                // wait for unchoke
                    // id = 1
                    // payload empty
                // request message -> can be parallel
                    // id = 6
                    // payload
                        // index of the piece
                        // begin byte (based on the block)
                        // length of block
                // wait piece message
                    // id = 7
                    // payload
                        // index of piece
                        // begin byte
                        // block data

                // file     -> piece        -> blocks
                // length   -> piece lenght  -> 2^14 (last is piece length % 2^14)
                // blocks can be requested in parallel


                // combine blocks
                // verify piece hash

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

                var encodedMessage = formatToString(decodeMessage(fileAsByteArr, index), Set.of("announce", "info"));
                md = MessageDigest.getInstance("SHA-1");
                md.update(encodeMessage(extractElement((Map<String, Object>)encodedMessage, "info")));
                System.out.println("hash from decoded/encoded message: " + byteArrayToHexString(md.digest()));
            }
            break;
            default:
            throw new RuntimeException("unsupported operation");
        }
    }

    public static String[] formatPeersResp(HttpResponse<byte[]> resp){
        assert resp != null;
        assert resp.body() != null;

        var formattedResp = stringifyKeys(decodeMessage(resp.body(), main.getNewReference(0)));
        byte[][] peers = arrToMatrix(extractElement((Map<String, byte[]>)formattedResp, "peers"), 6);

        var peersFormatted = new String[peers.length];

        for(int i = 0; i < peers.length; i++){
            var sb = new StringBuilder();
            for(int j = 0; j < 4; j++){
                sb.append(Integer.toUnsignedLong(peers[i][j] & 0xff));
                if(j != 3) sb.append(".");
            }
            // big endian
            sb.append(":").append(((peers[i][4] & 0xff) << 8) | (peers[i][5] & 0xff));
            peersFormatted[i] = sb.toString();
        }

        return peersFormatted;
    }

    public static HttpRequest createPeerRequest(
        String url,
        String peerId,
        Integer port,
        Integer uploaded,
        Integer downloaded,
        Long bytesLeft,
        byte[] hash
    ){
        assert url != null;
        peerId = peerId != null ? peerId : "01919491796102618370";
        assert port != null || port >= 1024 || port <= 49151;
        uploaded = uploaded != null ? uploaded : 0;
        downloaded = downloaded != null ? downloaded : 0;
        assert bytesLeft != null;
        assert hash != null && hash.length > 0;

        return
            HttpRequest.newBuilder()
                .uri(URI.create(
                    url + "?" +
                    "peer_id" + "=" + URLEncoder.encode(peerId, StandardCharsets.UTF_8) + "&" +
                    "port" + "=" + URLEncoder.encode(String.valueOf(port), StandardCharsets.UTF_8) + "&" +
                    "uploaded" + "=" + URLEncoder.encode(String.valueOf(uploaded), StandardCharsets.UTF_8) + "&" +
                    "downloaded" + "=" + URLEncoder.encode(String.valueOf(downloaded), StandardCharsets.UTF_8) + "&" +
                    "left" + "=" + URLEncoder.encode(String.valueOf(bytesLeft), StandardCharsets.UTF_8) + "&" +
                    "compact" + "=" + URLEncoder.encode("1", StandardCharsets.UTF_8) + "&" +
                    "info_hash" + "=" + byteArrayToPercEncodedHexString(hash)
                )
            )
            .GET()
            .build();
    }

    public static byte[] calculateHash(byte[] content) throws Exception {
        assert content != null;
        assert content.length > 0;

        var md = MessageDigest.getInstance(HASH_TYPE);
        md.update(content);
        return md.digest();
    }

    public static Object getDecodedMessageFromFile(String path) throws Exception {
        assert path != null;
        assert !path.isEmpty();

        return decodeMessage(
            Files.readAllBytes(Paths.get(path)), // TODO: potentially could be improved by reading only the bytes needed every time
            main.getNewReference(0)
        );
    }

    public static <T> T extractElement(Map<String, T> map, String name){
        return map.get(name);
    }

    public static byte[][] arrToMatrix(byte[] hashes, Integer size){
        assert hashes != null;
        assert size != null;
        assert size > 0;

        var resp = new byte[hashes.length % size == 0 ? hashes.length / size : (hashes.length / size) + 1][size];
        for(int i = 0; i < hashes.length; i++){
            resp[i / size][i % size] = hashes[i];
        }

        return resp;
    }

    public static String byteArrayToHexString(byte[] b) {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }

    public static String byteArrayToPercEncodedHexString(byte[] b) {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result += "%" + Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }

    public static Object formatToString(Object message){
        return formatToString(message, null);
    }

    /*
     This method is meant to be called on an already decoded map,
     keep in mind, after decoding the keys are already in their decoded format
     even though they are stored as byte array
    */
    public static Object stringifyKeys(Object object){
        assert object != null;
        assert object instanceof Map;

        var res = new LinkedHashMap<String, Object>();
        for(Map.Entry entry : ((Map<Object, Object>)object).entrySet()){
            if(entry == null || entry.getKey() == null) continue;
            Object formattedVal = entry.getValue();
            if(formattedVal instanceof Map) formattedVal = stringifyKeys((Map)entry.getValue());
            res.put(new String((byte[])entry.getKey()), formattedVal);
        }

        return res;
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
                var map = new LinkedHashMap<Object, Object>();
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
        assert firstColonIndex >= 0;
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

    public static LinkedHashMap<byte[], Object> decodeBencodeDictionary(byte[] bencodedString, Reference<Integer> index) {
        assert index.getValue() < bencodedString.length - 1;

        if(bencodedString == null) return null;
        if(bencodedString.length == 0) return new LinkedHashMap<byte[], Object>();

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
        assert from >= 0 && from < message.length;
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
   *  this can also be used to pass a reference to anything when the pass by value policy of java is not what you need,
   *  however, keep in mind the java way of doing it would be to encapsulate all the needed info in an object and reference
   *  the field of the object
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

    public <T> Reference<T> getNewReference(T v){
        return main.new Reference<T>(v);
    }
}
