package ca.ubc.cs.cs317.dnslookup;

import java.net.*;
import java.io.DataOutputStream;
import java.net.DatagramSocket;
import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.*;

public class DNSResponse {

    public DNSHeader dnsHeader = new DNSHeader();
    public DNSQues dnsQuestion = new DNSQues();
    public Set<ResourceRecord> answers = new HashSet<>();
    public Set<ResourceRecord> nameServers = new HashSet<>();
    public Set<ResourceRecord> additional = new HashSet<>();

    public void decode(byte[] buffer, int length) throws Exception {

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer, 0, length);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);

        dnsHeader.deserialize(dataInputStream);
        dnsQuestion.deserialize(dataInputStream, buffer, length);
        for (int i = 0; i < (dnsHeader.ANCOUNT + dnsHeader.ARCOUNT + dnsHeader.NSCOUNT); i++) {

            // Deserealize Name, Type, Class
            DNSQues dnsResponseQues = new DNSQues();
            dnsResponseQues.deserialize(dataInputStream, buffer, length);

            // Deserealize TTL
            long TTL = dataInputStream.readInt();
            TTL = TTL & 0xffffffff;

            // Deserealize RDLENGTH
            int RDLENGTH = dataInputStream.readShort();
            RDLENGTH = RDLENGTH & 0xffff;

            // Read RDATA as per given RDLENGTH
            byte[] RDATA = new byte[RDLENGTH];
            dataInputStream.read(RDATA, 0, RDLENGTH);

            // Generate the resource record to be stored
            ResourceRecord resourceRecord;

            RecordType rt = RecordType.getByCode(dnsResponseQues.TYPE);
            if (rt == RecordType.A) {
                resourceRecord = new ResourceRecord(dnsResponseQues.NAME, rt, TTL,
                        Inet4Address.getByAddress(dnsResponseQues.NAME, RDATA));
            } else if (rt == RecordType.AAAA) {
                resourceRecord = new ResourceRecord(dnsResponseQues.NAME, rt, TTL,
                        Inet6Address.getByAddress(dnsResponseQues.NAME, RDATA));
            } else if (rt == RecordType.NS || rt == RecordType.CNAME) {

                ByteArrayInputStream newByteArrayInputStream = new ByteArrayInputStream(RDATA, 0, RDLENGTH);
                DataInputStream newDataInputStream = new DataInputStream(newByteArrayInputStream);

                // resolve hostname
                String hostName = DNSQues.labelsToNameRec(newDataInputStream, buffer, length);

                resourceRecord = new ResourceRecord(dnsResponseQues.NAME, RecordType.getByCode(dnsResponseQues.TYPE),
                        TTL, hostName.substring(0, hostName.length() - 1));
            } else {
                resourceRecord = new ResourceRecord(dnsResponseQues.NAME, RecordType.getByCode(dnsResponseQues.TYPE),
                        TTL, "----");
            }

            // add records to the correct set
            if (i < dnsHeader.ANCOUNT) {
                answers.add(resourceRecord);
            } else if (i < dnsHeader.ANCOUNT + dnsHeader.NSCOUNT) {
                nameServers.add(resourceRecord);
            } else {
                additional.add(resourceRecord);
            }
        }

    }

    public static DNSResponse receiveDNS(DatagramSocket socket) throws Exception {

        // Create empty buffer for a packet
        byte[] receiveBuf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(receiveBuf, receiveBuf.length);

        // Write packet to buffer
        socket.receive(packet);

        // Decode packet
        DNSResponse dnsResponse = new DNSResponse();
        dnsResponse.decode(receiveBuf, packet.getLength());

        return dnsResponse;
    }

    // cache answers and additional which will have IP addresses
    public void addToCache(DNSCache cache) {

        for (ResourceRecord record : answers) {
            cache.addResult(record);
        }

        for (ResourceRecord record : nameServers) {
            cache.addResult(record);
        }

        for (ResourceRecord record : additional) {
            cache.addResult(record);
        }
    }

    public void print() {

        System.out.println("Response ID: " + dnsHeader.ID + " " + "Authoritative = " + (dnsHeader.AA == 1));

        PrintResourceRecordSet("Answers", this.answers);
        PrintResourceRecordSet("Nameservers", this.nameServers);
        PrintResourceRecordSet("Additional Information", this.additional);
    }

    private void PrintResourceRecordSet(String recordType, Set<ResourceRecord> resourceRecords) {

        System.out.println("  " + recordType + " " + "(" + resourceRecords.size() + ")");

        for (ResourceRecord record : resourceRecords) {
            PrintResourceRecord(record, record.getType().getCode());
        }
    }

    private void PrintResourceRecord(ResourceRecord record, int rtype) {

        System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(), record.getTTL(), record.getType(),
                record.getTextResult());
    }

}
