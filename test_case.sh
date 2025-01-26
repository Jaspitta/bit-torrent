mvn clean package
for input in "lli617e9:blueberryee", "lli4eei5ee", "l6:bananai781ee", "lli781e6:bananaee", "i4294967300e", "d3:foo3:bar5:helloi52ee", "d3:foo5:grape5:helloi52ee"
do
    echo "input is : $input"
	java -ea -jar ./target/java_bittorrent.jar "decode" "$input"
done

java -ea -jar ./target/java_bittorrent.jar "test" "sample.torrent"
java -ea -jar ./target/java_bittorrent.jar "info" "sample.torrent"
java -ea -jar ./target/java_bittorrent.jar "peers" "sample.torrent"
java -ea -jar ./target/java_bittorrent.jar "handshake" "sample.torrent" "165.232.41.73:51556"

java -ea -jar ./target/java_bittorrent.jar "download_piece" "-o" "./test-piece" "sample.torrent" 0
