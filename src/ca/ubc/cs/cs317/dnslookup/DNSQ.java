package ca.ubc.cs.cs317.dnslookup;

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


}