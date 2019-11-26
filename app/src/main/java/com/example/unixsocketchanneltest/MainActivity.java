package com.example.unixsocketchanneltest;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalServerSocket;
import android.os.Bundle;
import android.os.StrictMode;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        new Thread() {
                public void run() {
                    try {
                        // setupServer();
                        setupUnixServer();
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
        }.start();

        sleep(500);

        try {
            // setupClient();
            // setupUnixClient();
            setupUnixAsyncClient();
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void log(String s) {
        System.out.println(s);
    }

    void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    void setupUnixClient() throws IOException {
        LocalSocket ls = new LocalSocket();
        ls.connect(new LocalSocketAddress("android.net.LocalSocketTest"));
        ls.getOutputStream().write(42);
    }

    void setupUnixAsyncClient() throws IOException {
        // TODO
        SelectorProvider sp = SelectorProvider.provider();
        UnixSocketChannel ch = new UnixSocketChannel(sp);
        SocketAddress address = new InetSocketAddress("android.net.LocalSocketTest", 0);

        Util.print("starting connect");

        while (true) {
            boolean connected = ch.connect(address);
            Util.print("connected: " + connected);
            if (connected) {
                break;
            }
            Util.sleep(1000);
        }

    }

    void setupUnixServer() throws IOException {
        LocalServerSocket ss = new LocalServerSocket("android.net.LocalSocketTest");
        LocalSocket ls1 = ss.accept();

        while (true) {
            InputStream is = ls1.getInputStream();
            if (is.available() > 0) {
                log("received unix socket: " + is.read());
            } else {
                sleep(500);
            }
        }
    }

    void setupClient() throws IOException, InterruptedException {
        InetSocketAddress crunchifyAddr = new InetSocketAddress("localhost", 1111);
        SocketChannel crunchifyClient = SocketChannel.open(crunchifyAddr);

        log("Connecting to Server on port 1111...");

        ArrayList<String> companyDetails = new ArrayList<String>();

        companyDetails.add("Facebook");
        companyDetails.add("Twitter");
        companyDetails.add("IBM");
        companyDetails.add("Google");
        companyDetails.add("Crunchify");

        for (String companyName : companyDetails) {

            byte[] message = new String(companyName).getBytes();
            ByteBuffer buffer = ByteBuffer.wrap(message);
            crunchifyClient.write(buffer);

            log("sending: " + companyName);
            buffer.clear();

            // wait for 2 seconds before sending next message
            Thread.sleep(2000);
        }
        crunchifyClient.close();

    }
}
