package chatting;

import java.util.ArrayList;

public class ChatRoom2 {
    int chatRoomNum;
    ArrayList<User2> user2s;

    ChatRoom2(int chatRoomNum) {
        this.chatRoomNum = chatRoomNum;
        this.user2s = new ArrayList<>();
    }

    public void putUser(User2 user2) {
        user2s.add(user2);
    }

    public int getChatRoomNum() {
        return chatRoomNum;
    }

    public void setChatRoomNum(int chatRoomNum) {
        this.chatRoomNum = chatRoomNum;
    }

    public ArrayList<User2> getUsers() {
        return user2s;
    }
}
