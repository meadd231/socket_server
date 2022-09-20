package chatting;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class MySocketServer extends Thread {
    static ArrayList<User2> user2List = new ArrayList<>();
    static ArrayList<Socket> list = new ArrayList<>(); // 유저 확인용
    static ArrayList<ChatRoom2> chatRoom2List = new ArrayList<>();
    static Socket socket = null;
    User2 user2;

    public MySocketServer(Socket socket) {
        this.socket = socket; // 유저 socket을 할당
        list.add(socket); // 유저를 list에 추가
        user2 = new User2(socket);
        user2List.add(user2);
    }
    // Thread 에서 start() 메소드 사용 시 자동으로 해당 메소드 시작 (Thread별로 개별적 수행)
    public void run() {
        try {
            // 이 쓰레드 안에서 사용 할 수 있게 하는 변수
            ChatRoom2 nowChatRoom2 = null;
            User2 nowUser2 = user2;
            // 연결 확인용
            System.out.println("서버 : " + socket.getInetAddress()
                    + " IP의 클라이언트와 연결되었습니다");

            // InputStream - 클라이언트에서 보낸 메세지 읽기
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            // OutputStream - 서버에서 클라이언트로 메세지 보내기
            OutputStream out = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(out, true);

            // 클라이언트에게 연결되었다는 메세지 보내기
            writer.println("서버에 연결되었습니다! ID를 입력해 주세요!");

            String readValue; // Client에서 보낸 값 저장
            String name = null; // 클라이언트 이름 설정용
            int roomNum = 0;
            boolean identify = false;
            boolean isInTheRoom = false;

            // 채팅방 리스트에 채팅방이 10개 생김
            for (int i = 0; i < 10; i++) {
                chatRoom2List.add(new ChatRoom2(i));
            }

            // 클라이언트가 메세지 입력시마다 수행
            while((readValue = reader.readLine()) != null ) {
                if(!identify) { // 연결 후 한번만 노출
                    name = readValue; // 이름 할당
                    identify = true;
                    System.out.println(name + "님이 접속하셨습니다.");
                    writer.println(name + "님이 접속하셨습니다.");
                    writer.println("입장하고 싶은 방 번호를 입력해 주세요 (0부터 9까지)");
                    continue;
                }

                if (!isInTheRoom) {
                    roomNum = Integer.parseInt(readValue);
                    System.out.println(roomNum);
                    chatRoom2List.get(roomNum).putUser(nowUser2);
                    nowChatRoom2 = chatRoom2List.get(roomNum);
                    for (int i = 0; i< chatRoom2List.get(roomNum).getUsers().size(); i++) {
                        out = chatRoom2List.get(roomNum).getUsers().get(i).getSocket().getOutputStream();
                        writer = new PrintWriter(out, true);
                        // 클라이언트에게 메세지 발송
                        writer.println(name + "님이 "+ chatRoom2List.get(roomNum).getChatRoomNum()+"번 채팅방에 입장했습니다");
                    }
                    isInTheRoom = true;
                    continue;
                }
                // 나가기 하는 코드
                if (readValue.equals("나가기")) {
                    for (int i = 0; i< chatRoom2List.get(roomNum).getUsers().size(); i++) {
                        out = chatRoom2List.get(roomNum).getUsers().get(i).getSocket().getOutputStream();
                        writer = new PrintWriter(out, true);
                        // 클라이언트에게 메세지 발송
                        writer.println(name + "님이 "+ chatRoom2List.get(roomNum).getChatRoomNum()+"번 채팅방에서 나갔습니다");
                    }
                    chatRoom2List.get(roomNum).getUsers().remove(nowUser2);
                    isInTheRoom = false;
                    writer.println("입장하고 싶은 방 번호를 입력해 주세요 (0부터 9까지)");
                    continue;
                }

                // list 안에 클라이언트 정보가 담겨있음
                // 모든 클라이언트에게 메시지를 전송하기 위해 for문을 사용함
                // 여기서 채팅방에 있는 애들한테만 보내주기 위한 코드를 작성해야 겠는데?
                System.out.println(readValue);
//                for (int i=0; i<chatRoomList.get(roomNum).getUsers().size(); i++) {
//                    out = chatRoomList.get(roomNum).getUsers().get(i).getSocket().getOutputStream();
//                    writer = new PrintWriter(out, true);
//                    // 클라이언트에게 메세지 발송
//                    writer.println("("+nowChatRoom.chatRoomNum+") " + name + " : " + readValue);
//                }
                for (int i = 0; i<list.size(); i++) {
                    out = list.get(i).getOutputStream();
                    writer = new PrintWriter(out, true);
                    // 클라이언트에게 메세지 발송
                    writer.println("("+ list.size() +")"+name + " : " + readValue);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // 예외처리
        }
    }

    //메인함수가 시작되면, 서버 소켓이 생성되고, 클라이언트 접속시 쓰레드를 하나 만들게 됨
    public static void main(String[] args) {
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
                // Thread 안에 클라이언트 정보를 담아줌
                Thread thd = new MySocketServer(socketUser);
                thd.start(); // Thread 시작
            }

        } catch (IOException e) {
            e.printStackTrace(); // 예외처리
        }

    }

}
