package ca.ubc.cs.cs317.dnslookup;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.*;

// DNSQuery combines DNSHeader and DNSQues to create a query
public class DNSQuery {

    private static final int DEFAULT_DNS_PORT = 53;
    public final int queryID;

    public DNSHeader dnsHeader;
    public DNSQues dnsQuestion;

    public DNSQuery(DNSNode node, int queryID) {
        // fixed values for this assignment
        this.queryID = queryID;
        dnsHeader = new DNSHeader(queryID, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);

        dnsQuestion = new DNSQues(node.getHostName(), node.getType().getCode());
    }

    // combine DNSHeader and DNSQues to a bytes array of query
    public byte[] toBytes() throws Exception {

        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOS);

        dnsHeader.serialize(dataOutputStream);
        dnsQuestion.serialize(dataOutputStream);

        return byteArrayOS.toByteArray();
    }

    // uses this.toBytes() to create a packet and sends to a server
    public void sendPacket(DatagramSocket socket, InetAddress server) throws Exception {
        byte[] sendBuffer = this.toBytes();
        DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, server, DEFAULT_DNS_PORT);
        socket.send(packet);
    }

}