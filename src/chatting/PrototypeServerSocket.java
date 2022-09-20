package chatting;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class PrototypeServerSocket extends Thread {
    static HashMap<Integer, User> users = new HashMap<>();
    static HashMap<Integer, ChatRoom> chatRooms = new HashMap<>();

    static Connection conn; // DB와 연결하는 객체

    // 연결되고 처음에 받게됨
    int profileId;

    // 메세지가 올때마다 매번 바뀜
    int chatRoomId;

    Socket socket; // 클라이언트 소켓이 저장됨

    public PrototypeServerSocket(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            // 연결 확인용
            System.out.println("서버 : " + socket.getInetAddress()
                    + " IP의 클라이언트와 연결되었습니다");

            // InputStream - 클라이언트에서 보낸 메세지 읽는 객체
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

            String readValue; // Client에서 보낸 값 저장
            boolean identified = false; // 처음에 profileId가 오면 true가 됨
            JSONParser parser = new JSONParser(); // JSON 파싱 객체
            // 클라이언트가 메세지 입력시마다 수행
            while ( (readValue = reader.readLine()) != null ) {
                // 클라이언트 소켓과 연결 되자마자 profileId를 받아온다.
                if(!identified) { // 연결 후 한번만 노출
                    profileId = Integer.parseInt(readValue); // profileId 할당
                    identified = true;
                    users.get(profileId).setSocket(socket);
                    System.out.println(profileId + "님이 접속하셨습니다.");
                    // TODO 여기서 접속에 성공했는지 여부를 보내줘야 할까? 이건 사실 안드로이드에서 해줘야 할 것 같음
                    continue;
                }
                // 테스트를 위해 받은 데이터 print 해주기
                System.out.println("클라이언트에게 받은 데이터 :"+readValue);
                // 받은 데이터 파싱
                JSONObject readJson = (JSONObject) parser.parse(readValue);

                processSocketMessage(readJson); // 받은 메세지 처리
            } // while

        } catch (Exception e) {
            e.printStackTrace(); // 예외처리
        }
    }


    // 받은 메세지 처리
    private void processSocketMessage(JSONObject readJson) {
        // 보낼 데이터 jsonObject 생성
        JSONObject writeJson = new JSONObject();
        /*
            messageType
            1 - 텍스트 채팅 메세지
            2 - 이미지 채팅 메세지
            3 - 채팅방 입장
            4 - 채팅방에서 채팅 바로 받아 봄.
            20 - 팔로우 메세지
        */
        int messageType = ((Long) readJson.get("message_type")).intValue();

        if (messageType == 5) {
            SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String now = datetimeFormat.format(System.currentTimeMillis());

            insertFollowAlarmDataToMysql(readJson, now);
            readJson.put("created", now);
            readJson.put("alarm_id", getCreatedAlarmId());

            String targetId = ((Long) readJson.get("target_id")).toString();
            users.get(Integer.parseInt(targetId)).sendMessageToClient(readJson.toJSONString());
            return;
        }

        chatRoomId = ((Long) readJson.get("chat_room_id")).intValue();

        // 3 == 채팅방 입장시 읽은 채팅 갱신 해줌, 4 == 채팅방에서 채팅을 받았을 때 바로 갱신
        if (3 == messageType || 4 == messageType) {
            updateAndroidChatRoom(writeJson, readJson); // 안드로이드의 채팅방 갱신해주는 메세지 발송
            return;
        }

        String message = (String) readJson.get("chat_message");

        // 채팅방 id가 0 이면 새로 채팅방을 만들어 주고, chatRoomId 새로 받아서 바꿔줌
        if (0 == chatRoomId) {
            createNewChatRoom(writeJson, readJson);
        }

        if (messageType == 1) {
            // 텍스트 메세지를 받은 경우
            insertChatMessage(message); // 채팅내용 저장
        } else if (messageType == 2) {
            // 이미지를 받은 경우
            updateImageChatMessage(message);
        } else if (messageType == 20 || messageType == 21 || messageType == 23) {
            // TODO: 2022-06-09 여기에 영상통화 데이터 mysql에 insert 쿼리 해야 함.
            insertVideoCallChatMessage(messageType, readJson);
        }
        updateLastChatContents(message, messageType); // 마지막 채팅 내용 변경
        updateChatDetail(); // chat_detail 갱신
        if (messageType == 6) {
            if (profileId == chatRooms.get(chatRoomId).getSeller().getProfileId()) {
                chatRooms.get(chatRoomId).getBuyer().sendMessageToClient(getParsedWriteMessage(writeJson, message, messageType, readJson));
            } else {
                chatRooms.get(chatRoomId).getSeller().sendMessageToClient(getParsedWriteMessage(writeJson, message, messageType, readJson));
            }
        } else {
            sendFinalMessage(getParsedWriteMessage(writeJson, message, messageType, readJson)); // 채팅방 구성원들에게 채팅을 보내줌
        }
    }

    // 마지막에 생긴 알림 아이디 받아오기
    int getCreatedAlarmId() {
        try {
            String sql = "SELECT id FROM alarm ORDER BY id DESC LIMIT 1";
            // 너무 바로 해서 안된 건가? 시간을 두고 찾아야 하는 건가?
            // sql = "SELECT chat_room_id from chat_room WHERE cr_item_id = ? AND seller_id = ? AND buyer_id = ? LIMIT 1";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet resultSet = pstmt.executeQuery(sql);
            resultSet.next();

            return resultSet.getInt("id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 알람 생성
    void insertFollowAlarmDataToMysql(JSONObject readJson, String now) {
        int targetId = ((Long) readJson.get("target_id")).intValue();
        int myProfileId = ((Long) readJson.get("my_profile_id")).intValue();
        String myNickName = (String) readJson.get("my_nick_name");

        try {
            String sql = "INSERT INTO alarm (target_id, alarm_type, core_data, alarm_content, created)" +
                    " VALUES (?,1,?,?,?)";

            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, targetId);
            pstmt.setInt(2, myProfileId);
            pstmt.setString(3, myNickName+"님이 회원님을 팔로우하기 시작했습니다");
            pstmt.setString(4, now);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 채팅방 갱신해주는 코드 3 == 채팅방에 들어왔을 때, 4 == 채팅방에서 채팅을 받았을 때
    private void updateAndroidChatRoom(JSONObject writeJson, JSONObject readJson) {
        int messagType = ((Long) readJson.get("message_type")).intValue();
        int chatRoomId = ((Long) readJson.get("chat_room_id")).intValue();

        updateLastReadChatId(chatRoomId, profileId); // DB에서 마지막에 읽은 채팅 갱신
        writeJson.put("message_type", messagType);
        writeJson.put("chat_room_id", chatRoomId);
        writeJson.put("c_profile_id", profileId);
        String writeMessage = writeJson.toJSONString();
        if (chatRooms.get(chatRoomId).getSeller().getProfileId() == profileId) {
            // 클라이언트는 판매자
            chatRooms.get(chatRoomId).getBuyer().sendMessageToClient(writeMessage);
        } else {
            // 클라이언트는 구매자
            chatRooms.get(chatRoomId).getSeller().sendMessageToClient(writeMessage);
        }
    }

    // chat_detail에 안 읽은 채팅 갯수 갱신하기
    void updateLastReadChatId(int chatRoomId, int profileId) {
        try {
            String sql = "UPDATE chat_detail SET last_read_chat_id = ? WHERE cd_chat_room_id = ? AND cd_profile_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, getLastChatId(chatRoomId));
            pstmt.setInt(2, chatRoomId);
            pstmt.setInt(3, profileId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 채팅방 생성
    void insertChatRoomToMysql(JSONObject readJson) {
        int itemId = ((Long) readJson.get("item_id")).intValue();
        int sellerId = ((Long) readJson.get("seller_id")).intValue();
        int buyerId = ((Long) readJson.get("buyer_id")).intValue();
        String sellerNickName = (String) readJson.get("seller_nick_name");
        String buyerNickName = (String) readJson.get("buyer_nick_name");

        try {
            String sql = "INSERT INTO chat_room (cr_item_id, seller_id, buyer_id, seller_nick_name, buyer_nick_name)" +
                    " VALUES (?,?,?,?,?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, itemId);
            pstmt.setInt(2, sellerId);
            pstmt.setInt(3, buyerId);
            pstmt.setString(4, sellerNickName);
            pstmt.setString(5, buyerNickName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // 방금 생긴 채팅방 아이디 받아오기
    int getCreatedChatRoomId() {
        try {
            String sql = "SELECT chat_room_id from chat_room ORDER BY chat_room_id DESC LIMIT 1";
            // 너무 바로 해서 안된 건가? 시간을 두고 찾아야 하는 건가?
            // sql = "SELECT chat_room_id from chat_room WHERE cr_item_id = ? AND seller_id = ? AND buyer_id = ? LIMIT 1";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet resultSet = pstmt.executeQuery(sql);
            resultSet.next();

            return resultSet.getInt("chat_room_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 채팅 디테일 저장하기
    void insertChatDetail(int profileId) {
        try {
            // 여기에서 DB에 저장해주는 코드를 작성하자
            String sql = "INSERT INTO chat_detail (cd_chat_room_id, cd_profile_id)" +
                    " VALUES (?,?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, chatRoomId);
            pstmt.setInt(2, profileId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // todo 이거 먼저 바꿔줘야 함. 시간을 보편적으로 사용할 수 있게 만들어야되
    String getNowTimeMinute() {
        // 현재 시간 반환 하기
        LocalDateTime now = LocalDateTime.now();
        String formatedNow = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String time = formatedNow.substring(11, 13);
        String minute = formatedNow.substring(14, 16);
        String timeMinute = time + "시 " + minute + "분";
        return formatedNow;
    }

    private void createNewChatRoom(JSONObject writeJson, JSONObject readJson) {
        int itemId = ((Long) readJson.get("item_id")).intValue();
        int sellerId = ((Long) readJson.get("seller_id")).intValue();
        int buyerId = ((Long) readJson.get("buyer_id")).intValue();
        String sellerNickName = (String) readJson.get("seller_nick_name");
        String buyerNickName = (String) readJson.get("buyer_nick_name");
        writeJson.put("item_id", itemId);
        writeJson.put("buyer_nick_name", buyerNickName);


        insertChatRoomToMysql(readJson); // 채팅방 정보 저장
        chatRoomId = getCreatedChatRoomId(); // 새로 생긴 채팅방 아이디로 교체 해줌

        // 채팅 디테일 insert (seller와 buyer 따로 진행해줌)
        insertChatDetail(sellerId);
        insertChatDetail(buyerId);

        // HashMap에 채팅방 추가 해주기
        chatRooms.put(chatRoomId, new ChatRoom(chatRoomId, itemId, users.get(sellerId), users.get(buyerId)));
    }

    // 채팅내용을 mysql에 저장함
    // 매개변수를 배열이나, hash, 데이터 클래스를 이용해서 해도 될 것 같기도 함
    void insertChatMessage(String message) {
        try {
            // 여기에서 DB에 저장해주는 코드를 작성하자
            String sql = "INSERT INTO chat (c_chat_room_id, c_profile_id, chat_message, chat_date)" +
                    " VALUES (?,?,?,NOW())";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, chatRoomId);
            pstmt.setInt(2, profileId);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void insertVideoCallChatMessage(int messageType, JSONObject readJson) {
        int my_profile_id = ((Long) readJson.get("my_profile_id")).intValue();
        try {
            if (messageType == 23) {
                String sql = "INSERT INTO chat (c_chat_room_id, c_profile_id, chat_message, chat_date, chat_type, sub_data)" +
                        " VALUES (?,?,?,NOW(), 3, ?)";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, chatRoomId);
                pstmt.setInt(2, my_profile_id);
                pstmt.setString(3, String.valueOf(messageType));
                pstmt.setString(4, (String) readJson.get("chat_message"));
                pstmt.executeUpdate();
            } else {
                // 여기에서 DB에 저장해주는 코드를 작성하자
                String sql = "INSERT INTO chat (c_chat_room_id, c_profile_id, chat_message, chat_date, chat_type)" +
                        " VALUES (?,?,?,NOW(), 3)";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, chatRoomId);
                pstmt.setInt(2, my_profile_id);
                pstmt.setString(3, String.valueOf(messageType));
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateImageChatMessage(String message) {
        try {
            String sql = "UPDATE chat SET c_chat_room_id = ?, chat_message = ? WHERE chat_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, chatRoomId);
            pstmt.setString(2, message);
            pstmt.setInt(3, Integer.parseInt(message));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 마지막 채팅 내용 변경하기
    void updateLastChatContents(String message, int messageType) {
        try {
            String sql = "UPDATE chat_room SET last_chat_message = ?, last_chat_date = NOW() WHERE chat_room_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            if (messageType == 1) {
                pstmt.setString(1, message);
            } else if (messageType == 2) {
                pstmt.setString(1, "사진을 보냈습니다");
            } else if (messageType == 6) {
                pstmt.setString(1, "동영상을 보냈습니다");
            } else if (messageType == 20) {
                pstmt.setString(1, "영상통화");
            } else if (messageType == 21) {
                pstmt.setString(1, "영상통화 취소");
            } else if (messageType == 23) {
                pstmt.setString(1, "영상통화 종료");
            }
            pstmt.setInt(2, chatRoomId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    int getLastChatId(int chatRoomId) {
        try {
            String sql = "SELECT chat_id from chat WHERE c_chat_room_id = "+chatRoomId+" ORDER BY chat_id DESC LIMIT 1";
            // 너무 바로 해서 안된 건가? 시간을 두고 찾아야 하는 건가?
            PreparedStatement pstmt = conn.prepareStatement(sql);
//            pstmt.setInt(1, chatRoomId);
//            pstmt.setInt(2, profileId);
            ResultSet resultSet = pstmt.executeQuery(sql);
            resultSet.next();

            // 여기에서 채팅방 아이디가 39인 채팅이 아예 없어서 채팅아이디를 받지 못한 것 같아
            return resultSet.getInt("chat_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // chat_detail 갱신
    void updateChatDetail() {
        try {
            String sql = "UPDATE chat_detail SET last_read_chat_id = ? WHERE cd_chat_room_id = ? AND cd_profile_id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, getLastChatId(chatRoomId));
            pstmt.setInt(2, chatRoomId);
            pstmt.setInt(3, profileId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getParsedWriteMessage(JSONObject writeJson, String message, int messageType, JSONObject readJson) {
        // 보낼 데이터 json으로 가공
        writeJson.put("message_type", messageType);
        writeJson.put("chat_room_id", chatRoomId);
        if (messageType == 21 || messageType == 23) {
            int my_profile_id = ((Long) readJson.get("my_profile_id")).intValue();
            writeJson.put("c_profile_id", my_profile_id);
        } else {
            writeJson.put("c_profile_id", profileId);
        }
        writeJson.put("profile_nick_name", getProfileNickName());
        if (messageType == 23) {
            writeJson.put("sub_data", readJson.get("chat_message"));
            writeJson.put("chat_message", messageType);
        } else {
            writeJson.put("chat_message", message);
        }
        writeJson.put("chat_date", getNowTimeMinute());
        return writeJson.toJSONString();
    }

    private String getProfileNickName() {
        String result = "";
        try {
            String sql = "SELECT nick_name from profile WHERE profile_id = "+profileId+" LIMIT 1";
            // 너무 바로 해서 안된 건가? 시간을 두고 찾아야 하는 건가?
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet resultSet = pstmt.executeQuery(sql);
            resultSet.next();

            // 여기에서 채팅방 아이디가 39인 채팅이 아예 없어서 채팅아이디를 받지 못한 것 같아
            result = resultSet.getString("nick_name");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void sendFinalMessage(String finalMessage) {
        // 받아온 채팅방 번호에 해당하는 채팅방 구성원들에게 채팅을 보내줌
        chatRooms.get(chatRoomId).getSeller().sendMessageToClient(finalMessage);
        chatRooms.get(chatRoomId).getBuyer().sendMessageToClient(finalMessage);
    }
















    // 메인함수가 시작되면, 서버 소켓이 생성되고, 클라이언트가 접속 할 때마다 쓰레드를 하나씩 만들게 됨
    // 앱을 서비스 할때, 서버 소켓은 계속 돌아가고 있어야 할 것 같음
    public static void main(String[] args) {
        setProgramUTF8(); // 프로그램 인코딩 UTF-8로 설정
        mysqlDriverLoading(); // mysql 드라이버 설정, 연결
        callMysqlData(); // mysql의 데이터 불러오기
        openServerSocket(); // 서버 소켓 열기
    }

    static void setProgramUTF8() {
        try {
            System.setProperty("file.encoding","UTF-8");
            Field charset = Charset.class.getDeclaredField("defaultCharset");
            charset.setAccessible(true);
            charset.set(null,null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // mysql 드라이버 로딩, DB 연결
    static void mysqlDriverLoading() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://3.36.116.4/team_nova_app1";
            conn = DriverManager.getConnection(url, "root", "marx1818ch!");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    // mysql의 데이터를 불러와서 자바 자료구조에 저장함
    static void callMysqlData() {
        callMysqlUserData();
        callMysqlChatRoomData();
    }

    static void callMysqlUserData() {
        try {
            String sql = "SELECT profile_id, nick_name from profile";
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            while(resultSet.next()) {
                // 레코드의 칼럼은 배열과 달리 0부터 시작하지 않고 1부터 시작한다.
                // 데이터베이스에서 가져오는 데이터의 타입에 맞게 getString 또는 getInt 등을 호출한다.
                int profile_id = resultSet.getInt(1);
                String nick_name = resultSet.getString(2);

                System.out.println(profile_id+" "+nick_name);

                users.put(profile_id, new User(profile_id, nick_name));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void callMysqlChatRoomData() {
        try {
            String sql = "SELECT * from chat_room";
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            while (resultSet.next()) {
                int chat_room_id = resultSet.getInt(1);
                int item_id = resultSet.getInt(2);
                int seller_id = resultSet.getInt("seller_id");
                int buyer_id = resultSet.getInt("buyer_id");
                System.out.println(chat_room_id+" "+item_id+" "+seller_id+" "+buyer_id);
                chatRooms.put(chat_room_id, new ChatRoom(chat_room_id, item_id, users.get(seller_id), users.get(buyer_id)));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void openServerSocket() {
        try {
            int socketPort = 1234; // 소켓 포트 설정용
            ServerSocket serverSocket = new ServerSocket(socketPort); // 서버 소켓 만들기

            // 서버 오픈 확인용
            System.out.println("socket : " + socketPort + "으로 서버가 열렸습니다");

            // 소켓 서버가 종료될 때까지 무한루프
            // 클라이언트가 서버에 접속함
            // 클라이언트가 서버에 접속할 때마다 새로 쓰레드를 만들어 주는 것 같음
            while (true) {
                Socket socketUser = serverSocket.accept(); // 서버에 클라이언트 접속 시
                // 하나의 Thread가 하나의 클라이언트 소켓과 연결됨
                Thread pss = new PrototypeServerSocket(socketUser);
                pss.start(); // Thread 시작
            }

        } catch (IOException e) {
            e.printStackTrace(); // 예외처리
        }
    }

}
