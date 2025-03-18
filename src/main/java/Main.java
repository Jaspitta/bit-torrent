import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
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
  private static final int HANDSHAKE_SIZE = 68;
  private static final int BLOCK_SIZE = (int)Math.pow(2, 14);

  @SuppressWarnings({"unchecked"})
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

        var ip = formatIp(args[2]);

        try(var clientSocket = new Socket(ip.getKey(), ip.getValue())){

          clientSocket.getOutputStream().write(buildHandshakeMessage(hash));

          // should I check the hash back is the same?
          var handShakeResp = clientSocket.getInputStream().readNBytes(HANDSHAKE_SIZE);

          for(int i = 0; i < hash.length; i ++)
          assert hash[i] == handShakeResp[i+28];

          System.out.println(
            "Peer ID: " +
            byteArrayToHexString(
              Arrays.copyOfRange(handShakeResp, 48, HANDSHAKE_SIZE)
            )
          );
        }
      }
      break;
      case "download_piece": {
        assert args != null;
        assert "-o".equals(args[1]);
        assert args[2] != null && !"".equals(args[2]);
        assert args[3] != null && !"".equals(args[3]);
        assert args[4] != null && !"".equals(args[4]);

        // get tracker url
        var decodedMessage = getDecodedMessageFromFile(args[3]);

        Object formattedFileContent = formatToString(decodedMessage, Set.of("announce", "info", "length"));

        // get peers
        Object info = extractElement((Map<String, Object>)formattedFileContent, "info");
        Long pieceLength = extractElement((Map<String, Long>)info, "piece length");
        var piecesHashes = arrToMatrix((extractElement((Map<String, byte[]>)info, "pieces")), 20);

        var hash = calculateHash(encodeMessage(info));
        var peerReq = createPeerRequest(
          extractElement((Map<String, String>)formattedFileContent, "announce"),
          null,
          6881,
          0,
          0,
          extractElement((Map<String, Long>)info, "length"),
          hash
        );

        var peer = formatPeersResp(
          HttpClient.newBuilder().build().send(
            peerReq,
            BodyHandlers.ofByteArray()
          )
        )[0];

        // hadshake with one
        var ip = formatIp(peer);
        try(var clientSocket = new Socket(ip.getKey(), ip.getValue())){

          // TODO: think how to implement this with parallelization/pipelining

          // unless specified otherwise each message integer is 4 bytes BE, except the 1 byte id
          var outStream = clientSocket.getOutputStream();
          outStream.write(buildHandshakeMessage(hash));

          var inStream = clientSocket.getInputStream();
          var handshakeResp = inStream.readNBytes(HANDSHAKE_SIZE);

          for(int i = 0; i < hash.length; i ++) assert hash[i] == handshakeResp[i+28];

          var bitFieldMessage = readNextMessage(inStream);
          assert bitFieldMessage[4] == PeerMessageType.BITFIELD.id;

          outStream.write(buildInterestedMessage());
          var unchokeMessage = readNextMessage(inStream);
          assert unchokeMessage[4] == PeerMessageType.UNCHOKE.id;

          // preparing file
          new File(args[2]).delete();
          assert new File(args[2]).createNewFile();

          var piece = getPiece(
            Integer.valueOf(args[4]),
            pieceLength.intValue(),
            outStream,
            inStream,
            piecesHashes[Integer.valueOf(args[4])]
          );

          try(var writer = new FileOutputStream(new File(args[2]))) {writer.write(piece);}

        }

      }
      break;
      case "download": {
        assert args != null;
        assert "-o".equals(args[1]);
        assert args[2] != null && !"".equals(args[2]);
        assert args[3] != null && !"".equals(args[3]);
        var decodedMessage = getDecodedMessageFromFile(args[3]);

        Object formattedFileContent = formatToString(decodedMessage, Set.of("announce", "info", "length"));

        // get peers
        Object info = extractElement((Map<String, Object>)formattedFileContent, "info");
        Long pieceLength = extractElement((Map<String, Long>)info, "piece length");
        var piecesHashes = arrToMatrix((extractElement((Map<String, byte[]>)info, "pieces")), 20);
        Long length = extractElement((Map<String, Long>)info, "length");

        var hash = calculateHash(encodeMessage(info));
        var peerReq = createPeerRequest(
          extractElement((Map<String, String>)formattedFileContent, "announce"),
          null,
          6881,
          0,
          0,
          extractElement((Map<String, Long>)info, "length"),
          hash
        );

        var peer = formatPeersResp(
          HttpClient.newBuilder().build().send(
            peerReq,
            BodyHandlers.ofByteArray()
          )
        )[0];

        // hadshake with one
        var ip = formatIp(peer);
        try(var clientSocket = new Socket(ip.getKey(), ip.getValue())){

          // TODO: think how to implement this with parallelization/pipelining

          // unless specified otherwise each message integer is 4 bytes BE, except the 1 byte id
          var outStream = clientSocket.getOutputStream();
          outStream.write(buildHandshakeMessage(hash));

          var inStream = clientSocket.getInputStream();
          var handshakeResp = inStream.readNBytes(HANDSHAKE_SIZE);

          for(int i = 0; i < hash.length; i ++)
          assert hash[i] == handshakeResp[i+28];

          var bitFieldMessage = readNextMessage(inStream);
          assert bitFieldMessage[4] == PeerMessageType.BITFIELD.id;

          outStream.write(buildInterestedMessage());
          var unchokeMessage = readNextMessage(inStream);
          assert unchokeMessage[4] == PeerMessageType.UNCHOKE.id;

          // preparing file
          new File(args[2]).delete();
          assert new File(args[2]).createNewFile();

          for(int i = 0; i < piecesHashes.length; i++){
          var piece = getPiece(
            i,
            i == piecesHashes.length - 1 ? length.intValue() % pieceLength.intValue() : pieceLength.intValue(),
            outStream,
            inStream,
            piecesHashes[i]
          );
          try(var writer = new FileOutputStream(new File(args[2]))) {writer.write(piece);}
          }


        }
      }
      break;
      default:
      throw new RuntimeException("unsupported operation");
    }
  }

  public static byte[] getPiece(int pieceIndex, int pieceLength, OutputStream outStream, InputStream inStream, byte[] pieceHash) throws Exception{
          byte[] piece = new byte[pieceLength];
          for(int i = 0; i < pieceLength; i += BLOCK_SIZE){
            outStream.write(
              buildRequestMessage(
                Integer.valueOf(pieceIndex),
                i,
                (int)Math.min(BLOCK_SIZE, pieceLength - i)
              )
            );
            byte[] pieceMessage = readNextMessage(inStream);
            assert pieceMessage[4] == PeerMessageType.PIECE.id;
            for(int j = 13 /*l + id + ind + begin*/; j < pieceMessage.length; j++) piece[i + j - 13] = pieceMessage[j];
          }

          assert Arrays.compare(pieceHash, calculateHash(piece)) == 0;
          return piece;
  }

  public static byte[] buildRequestMessage(int pieceIndex, int blockBegin, int blockLength){
    assert pieceIndex >= 0;
    assert blockBegin >= 0;
    assert blockLength >= 0;

    var id = (byte)PeerMessageType.REQUEST.id;
    var index = intTo4ByteBE(pieceIndex);
    var begin = intTo4ByteBE(blockBegin);
    var blockLengthB = intTo4ByteBE((int)blockLength);
    var length = intTo4ByteBE(1 /*id*/+ index.length + begin.length + blockLengthB.length);
    return combineArrays(length, new byte[]{id},/*paload -> */ index, begin, blockLengthB);
  }

  public static byte[] combineArrays(byte[]... arrs){
    if(arrs == null) return null;
    int totalLength = 0;
    for(byte[] arr : arrs) totalLength += arr.length;

    byte[] resp = new byte[totalLength];
    int nextSlot = 0;
    for(byte[] arr : arrs){
      for(byte b : arr){
        resp[nextSlot] = b;
        nextSlot++;
      }
    }
    return resp;
  }

  // java uses 2's complements
  // positive nums have first bit to 0
  public static byte[] intTo4ByteBE(int num){
    if(num == 0) return new byte[]{0,0,0,0};
    if(Integer.MAX_VALUE == num) return new byte[]{1,1,1,1};

    return new byte[]{
    (byte) ((num >>> 24) & 0xff),
    (byte) ((num >>> 16) & 0xff),
    (byte) ((num >>> 8) & 0xff),
    (byte) (num & 0xff),
    };
  }

  public static byte[] buildInterestedMessage(){
    return new byte[]{
      0,0,0,1, // length
      2 // id
      // payload is empty
    };
  }

  public static byte[] readNextMessage(InputStream inStream) throws IOException, InterruptedException{
    assert inStream != null;

    var rawLength = inStream.readNBytes(4);
    var length = fourByteBeToInt(rawLength);

    var message = new byte[length + 4];
    for(int i = 0; i < 4; i++) message[i] = rawLength[i];

    var remainingMessage = inStream.readNBytes(length);

    for(int i = 0; i < remainingMessage.length; i++) message[i+4] = remainingMessage[i];

    return message;
  }

  public static int fourByteBeToInt(byte[] message){
    assert message.length >= 4;
    return  ((message[0] & 0xff) << 24) |
            ((message[1] & 0xff) << 16) |
            ((message[2] & 0xff) << 8)  |
            ((message[3] & 0xff));
  }


  public static byte[] buildHandshakeMessage(byte[] hash){
    assert hash != null && hash.length == 20;
    //
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
    return message;
  }

  public static Map.Entry<String, Integer> formatIp(String address){
    assert address != null && !"".equals(address);

    var separatorIndex = address.indexOf(':');
    return Map.entry(
      address.substring(0, separatorIndex),
      Integer.valueOf(address.substring(separatorIndex+1, address.length()))
    );
  }

  @SuppressWarnings("unchecked")
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
    assert port != null && port >= 1024 && port <= 49151;
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

    var resp = new byte[(int)Math.ceil(hashes.length / size)][size];
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
  @SuppressWarnings({"unchecked", "rawtypes"})
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

  @SuppressWarnings({"unchecked", "rawtypes"})
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

  @SuppressWarnings({"rawtypes"})
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

  @SuppressWarnings({"rawtypes"})
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

  @SuppressWarnings({"unchecked", "rawtypes"})
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

  public enum PeerMessageType {
    BITFIELD(5),
    INTERESTED(2),
    UNCHOKE(1),
    REQUEST(6),
    PIECE(7);

    private final int id;

    PeerMessageType(int id){
      this.id = id;
    }

    public int getId(){
      return this.id;
    }
  }

}
