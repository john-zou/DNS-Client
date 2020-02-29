package ca.ubc.cs.cs317.dnslookup;

import java.io.DataOutputStream;

// DNS Question for both query and response
public class DNSQ {
    public String NAME;
    public int TYPE;
    public int CLASS = 1;

    public DNSQ(){
    }

    public DNSQ(String NAME, int TYPE) {

        this.NAME = NAME;
        this.TYPE = TYPE;
    }

    // write the Question to the output stream in the right format
    public void serialize(DataOutputStream dataOutputStream) throws Exception {

        // write the domain name as labels
        String[] labels = NAME.split(".");
        for(String label: labels) {

            dataOutputStream.writeByte(label.length());
            dataOutputStream.writeBytes(label);
        }
        dataOutputStream.writeByte(0);

        // write type and class
        dataOutputStream.writeShort(TYPE);
        dataOutputStream.writeShort(CLASS);
    }

}