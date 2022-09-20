package chatting;


import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class User {
    int profileId;
    String nickName;
    Socket socket = null;
    PrintWriter writer;

    public User(int profileId, String nickName) {
        this.profileId = profileId;
        this.nickName = nickName;
    }

    public int getProfileId() {
        return profileId;
    }

    public void setProfileId(int profileId) {
        this.profileId = profileId;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    // 유저의 소켓을 받아드리고, 송신 객체 생성
    public void setSocket(Socket socket) throws IOException {
        this.socket = socket;
        OutputStream out = socket.getOutputStream();
        writer = new PrintWriter(out,true);
    }

    // 유저의 클라이언트 소켓으로 메세지 송신
    public void sendMessageToClient(String chatMessage) {
        if (socket != null) {
            // 소켓 연결이 되어있는 경우
            writer.println(chatMessage);
            System.out.println("클라이언트에게 전송할 데이터 :"+chatMessage);
        } else {
            System.out.println("연결 안되있음");
        }
    }
}
