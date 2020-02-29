package ca.ubc.cs.cs317.dnslookup;

import java.io.DataInputStream;
import java.io.DataOutputStream;

// Generic DNS Header for both query and response

public class DNSHeader {

    public int ID, QR, OPCODE, AA, TC, RD, RA, Z, RCODE, QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT;

    // for serealize
    public DNSHeader(int ID, int QR, int OPCODE, int AA, int TC, int RD, int RA, int Z, int RCODE,
                     int QDCOUNT, int ANCOUNT, int NSCOUNT, int ARCOUNT) {

        this.ID = ID;
        this.QR = QR;
        this.OPCODE = OPCODE;
        this.AA = AA;
        this.TC = TC;
        this.RD = RD;
        this.RA = RA;
        this.Z = Z;
        this.RCODE = RCODE;
        this.QDCOUNT = QDCOUNT;
        this.ANCOUNT = ANCOUNT;
        this.NSCOUNT = NSCOUNT;
        this.ARCOUNT = ARCOUNT;
    }

    // for deserealize
    public DNSHeader(){
    }


    // create a series of bits for parameters using bit shift operation
    private int getParams() {

        int params = (QR << 15);
        params = params ^ (OPCODE << 11);
        params = params ^ (AA << 10);
        params = params ^ (TC << 9);
        params = params ^ (RD << 8);
        params = params ^ (RA << 7);
        params = params ^ (Z << 4);
        params = params ^ RCODE;

        return params;
    }

    // set individual params from a series of bit (from input stream)
    private void setParams(int params) {

        params = params & 0xffff;

        QR = (params >>> 15) & 0b1;
        OPCODE = (params >>> 11) & 0b1111;
        AA = (params >>> 10) & 0b1;
        TC = (params >>> 9) & 0b1;
        RD = (params >>> 8) & 0b1;
        RA = (params >>> 7) & 0b1;
        Z = (params >>> 4) & 0b111;
        RCODE = params & 0b1111;
    }

    // Serialize Object to the output datastream in order
    public void serialize(DataOutputStream dataOutputStream) throws Exception {

        dataOutputStream.writeShort(ID);
        dataOutputStream.writeShort(getParams());
        dataOutputStream.writeShort(QDCOUNT);
        dataOutputStream.writeShort(ANCOUNT);
        dataOutputStream.writeShort(NSCOUNT);
        dataOutputStream.writeShort(ARCOUNT);
    }


    // DeSerialize Object from input datastream and set fields for object in order
    public void deserialize(DataInputStream dataInputStream) throws Exception {

        ID = dataInputStream.readUnsignedShort();
        setParams(dataInputStream.readUnsignedShort());
        QDCOUNT = dataInputStream.readUnsignedShort();
        ANCOUNT = dataInputStream.readUnsignedShort();
        NSCOUNT = dataInputStream.readUnsignedShort();
        ARCOUNT = dataInputStream.readUnsignedShort();
    }
}
