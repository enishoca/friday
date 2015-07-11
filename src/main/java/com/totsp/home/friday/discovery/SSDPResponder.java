package com.totsp.home.friday.discovery;

import java.io.IOException;
import java.net.*;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SSDPResponder extends Thread {
    private static final Logger LOGGER = Logger.getLogger(SSDPResponder.class.getCanonicalName());
    /**
     * Default IPv4 multicast address for SSDPResponder messages
     */
    public static final String BROADCAST_ADDRESS = "239.255.255.250";

    public static final String IPV6_LINK_LOCAL_ADDRESS = "FF02::C";
    public static final String IPV6_SUBNET_ADDRESS = "FF03::C";
    public static final String IPV6_ADMINISTRATIVE_ADDRESS = "FF04::C";
    public static final String IPV6_SITE_LOCAL_ADDRESS = "FF05::C";
    public static final String IPV6_GLOBAL_ADDRESS = "FF0E::C";

    public static final String ST = "ST";
    public static final String LOCATION = "LOCATION";
    public static final String NT = "NT";
    public static final String NTS = "NTS";

    /* Definitions of start line */
    public static final String SL_NOTIFY = "NOTIFY * HTTP/1.1";
    public static final String SL_MSEARCH = "M-SEARCH * HTTP/1.1";
    public static final String SL_OK = "HTTP/1.1 200 OK";

    /* Definitions of notification sub type */
    public static final String NTS_ALIVE = "ssdp:alive";
    public static final String NTS_BYEBYE = "ssdp:byebye";
    public static final String NTS_UPDATE = "ssdp:update";

    public static final String LOG_TAG = "SSDPResponder";

    private SocketAddress mMulticastGroupAddress = new InetSocketAddress("239.255.255.250", 1900);
    private MulticastSocket mMulticastSocket;
    private DatagramSocket mUnicastSocket;

    private final NetworkInterface networkInterface;

    private boolean mRunning = false;
    private final InetAddress bindAddress;

    public SSDPResponder(InetAddress bindAddress, NetworkInterface networkInterface) throws IOException {
        this.bindAddress = bindAddress;
        this.networkInterface = networkInterface;
    }

    @Override
    public synchronized void start() {
        mRunning = true;
        super.start();
    }

    @Override
    public void run() {
        try {
            mMulticastSocket = new MulticastSocket(1900);
            mMulticastSocket.setLoopbackMode(true);
            mMulticastSocket.joinGroup(mMulticastGroupAddress, networkInterface);

            mUnicastSocket = new DatagramSocket(null);
            mUnicastSocket.setReuseAddress(true);
            mUnicastSocket.bind(new InetSocketAddress(bindAddress,1900));

        } catch (IOException e) {
           LOGGER.log(Level.WARNING, "Setup SSDPResponder failed.", e);
        }
        while(mRunning) {
            DatagramPacket dp = null;
            try {
                dp = receive();


                String startLine = parseStartLine(dp);
                if(startLine.equals(SL_MSEARCH)) {
                    String st = parseHeaderValue(dp, ST);

                    if(st.contains("dial-multiscreen-org:service:dial:1")) {

                        String responsePayload = "HTTP/1.1 200 OK\n" +
                                "ST: urn:dial-multiscreen-org:service:dial:1\n"+
                                "HOST: "+ BROADCAST_ADDRESS +":1900\n"+
                                "EXT:\n"+
                                "CACHE-CONTROL: max-age=1800\n"+
                                LOCATION+": http://"+this.bindAddress.getHostAddress()+":80/ssdp/device-desc.xml\n" +
                                "CONFIGID.UPNP.ORG: 7339\n" + // TODO devie hash here
                                "BOOTID.UPNP.ORG: 7339\n" + // TODO devie hash here
                                "USN: uuid:"+ "\n\n"; // TODO device ID here


                        DatagramPacket response = new DatagramPacket(responsePayload.getBytes(), responsePayload.length(), new InetSocketAddress(dp.getAddress(),dp.getPort()));
                        mUnicastSocket.send(response);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "SSDPResponder fail.", e);
            }
        }
        LOGGER.log(Level.INFO, "SSDPResponder shutdown.");

    }

    public synchronized void shutdown() {
        mRunning = false;
    }

    private DatagramPacket receive() throws IOException {
        byte[] buf = new byte[1024];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        mMulticastSocket.receive(dp);

        return dp;
    }

    private String parseHeaderValue(String content, String headerName) {
        Scanner s = new Scanner(content);
        s.nextLine(); // Skip the start line

        while (s.hasNextLine()) {
            String line = s.nextLine();
            int index = line.indexOf(':');
            String header = line.substring(0, index);
            if (headerName.equalsIgnoreCase(header.trim())) {
                return line.substring(index + 1).trim();
            }
        }

        return null;
    }

    private String parseHeaderValue(DatagramPacket dp, String headerName) {
        return parseHeaderValue(new String(dp.getData()), headerName);
    }

    private String parseStartLine(String content) {
        Scanner s = new Scanner(content);
        return s.nextLine();
    }

    private String parseStartLine(DatagramPacket dp) {
        return parseStartLine(new String(dp.getData()));
    }
}