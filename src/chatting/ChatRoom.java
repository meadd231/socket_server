package chatting;

public class ChatRoom {
    private final int chatRoomId;
    private final int itemId;
    User seller;
    User buyer;

    public ChatRoom(int chatRoomId, int itemId, User seller, User buyer) {
        this.chatRoomId = chatRoomId;
        this.itemId = itemId;
        this.seller = seller;
        this.buyer = buyer;
    }

    public int getChatRoomId() {
        return chatRoomId;
    }

    public int getItemId() {
        return itemId;
    }

    public User getSeller() {
        return seller;
    }

    public User getBuyer() {
        return buyer;
    }
}
