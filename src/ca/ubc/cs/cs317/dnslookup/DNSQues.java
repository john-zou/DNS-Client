package ca.ubc.cs.cs317.dnslookup;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;


// DNS Question for both query and response
public class DNSQues {
    public String NAME;
    public int TYPE;
    public int CLASS = 1;

    public DNSQues(){
    }

    public DNSQues(String NAME, int TYPE) {

        this.NAME = NAME;
        this.TYPE = TYPE;
    }

    // write the Question to the output stream in the right format
    public void serialize(DataOutputStream dataOutputStream) throws Exception {

        // write the domain name as labels
        String[] labels = NAME.split("\\.");
        for(String label: labels) {

            dataOutputStream.writeByte(label.length());
            dataOutputStream.writeBytes(label);
        }
        dataOutputStream.writeByte(0);

        // write type and class
        dataOutputStream.writeShort(TYPE);
        dataOutputStream.writeShort(CLASS);
    }


    public void deserialize(DataInputStream dataInputStream, byte[] buffer, int length) throws Exception {

        NAME = this.labelsToNameRec(dataInputStream, buffer, length);
        // Remove "." after the name caused due to looping
        NAME = NAME.substring(0, NAME.length() - 1);

        TYPE = dataInputStream.readShort();
        CLASS = dataInputStream.readShort();
    }



    public static String labelsToNameRec(DataInputStream dataInputStream, byte[] buffer, int length) throws Exception {

        String result = "";

        // Reset buf 2 places for re-read
        dataInputStream.mark(2);

        int lengthOfSplit = dataInputStream.readByte();
        lengthOfSplit = lengthOfSplit & 0xff;


        while (lengthOfSplit != 0) {

            // Compression condition
            if ((lengthOfSplit >>> 6) == 3) {

                dataInputStream.reset();

                int pointer = dataInputStream.readUnsignedShort();
                pointer = pointer & 0x3FFF;

                // create new stream to append to name
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer, pointer, length - pointer);
                DataInputStream newDataInputStream = new DataInputStream(byteArrayInputStream);

                result += labelsToNameRec(newDataInputStream, buffer, length);

                break;

            } else {

                byte[] split = new byte[lengthOfSplit];
                dataInputStream.read(split, 0, lengthOfSplit);


                result += (new String(split));
                result += ".";

                dataInputStream.mark(2);

                lengthOfSplit = dataInputStream.readByte();
                lengthOfSplit = lengthOfSplit & 0xff;
            }
        }

        return result;
    }

}