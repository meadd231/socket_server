package chatting;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class WritingThread extends Thread { // 서버로 메세지 보내는 Thread
    Socket socket;
    // 채팅용 Scanner
    Scanner scanner = new Scanner(System.in);

    // 생성자
    public WritingThread(Socket socket) {
        // 받아온 Socket Parameter를 해당 클래스 Socket에 넣기
        this.socket = socket;
    }

    public void run() {
        try {
            // OutputStream - 클라이언트에서 Server로 메세지 발송
            // socket의 OutputStream 정보를 OutputStream out에 넣은 뒤
            OutputStream out = socket.getOutputStream();
            // PrintWriter에 위 OutputStream을 담아 사용
            PrintWriter writer = new PrintWriter(out, true);
            String sendMessage;
            while(true) { // 무한반복
                sendMessage = scanner.nextLine();
                writer.println(sendMessage); // 입력한 메세지 발송
            }

        } catch (Exception e) {
            e.printStackTrace(); // 예외처리
        }


    }


}
